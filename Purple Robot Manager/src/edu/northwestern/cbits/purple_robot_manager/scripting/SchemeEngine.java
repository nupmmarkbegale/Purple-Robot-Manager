package edu.northwestern.cbits.purple_robot_manager.scripting;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jscheme.JScheme;
import jsint.Evaluator;
import jsint.Pair;
import jsint.Symbol;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import edu.northwestern.cbits.purple_robot_manager.ManagerService;
import edu.northwestern.cbits.purple_robot_manager.ScheduleManager;
import edu.northwestern.cbits.purple_robot_manager.config.SchemeConfigFile;
import edu.northwestern.cbits.purple_robot_manager.probes.features.Feature;

public class SchemeEngine extends BaseScriptEngine
{
	public SchemeEngine(Context context, Map<String, Object> objects) 
	{
		super(context);
	}

	public static boolean canRun(String script) 
	{
		script = script.trim();

		if (script == null || script.length() < 1)
			return false;
		else if (script.charAt(0) == '(')
		{
			if (script.charAt(script.length() - 1) == ';')
				return false;
			else if (script.toLowerCase().contains("function("))
				return false;
			
			return true;
		}
		
		return false;
	}

	public Object evaluateSource(String source)
	{
		if (source.trim().toLowerCase().equals("(begin)"))
			return null;

		Evaluator eval = new Evaluator();
		eval.getInteractionEnvironment().setValue(Symbol.intern("PurpleRobot"), this);
		eval.getInteractionEnvironment().setValue(Symbol.intern("JSONHelper"), new JSONHelper());
		JScheme scheme = new JScheme(eval);

		try 
		{
			scheme.load(new InputStreamReader(this._context.getAssets().open("scheme/pregexp.scm")));
			scheme.load(new InputStreamReader(this._context.getAssets().open("scheme/json.scm")));
			scheme.load(new InputStreamReader(this._context.getAssets().open("scheme/purple-robot.scm")));
		} 
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		
		try
		{
			return scheme.eval(source);
		}
		catch (StackOverflowError e)
		{

		}
		
		return Boolean.valueOf(false);
	}

	protected String language() 
	{
		return "Scheme";
	}
	
	public boolean updateTrigger(String triggerId, Pair parameters)
	{
		Map<String, Object> paramsMap = SchemeEngine.parsePairList(parameters);
		paramsMap.put("identifier", triggerId);
		
		return this.updateTrigger(triggerId, paramsMap);
	}

	public boolean updateTrigger(Pair parameters)
	{
		Map<String, Object> paramsMap = SchemeEngine.parsePairList(parameters);

		if (paramsMap.containsKey("identifier"))
		{
			String triggerId = paramsMap.get("identifier").toString();
			
			return this.updateTrigger(triggerId, paramsMap);
		}
		
		return false;
	}

	public void scheduleScript(String identifier, String dateString, Pair action)
	{
		super.scheduleScript(identifier, dateString, action.toString());
	}
	
	public boolean updateProbe(Pair params)
	{
		Map<String, Object> map = SchemeEngine.parsePairList(params);

		return this.updateProbe(map);
	}

	private static Map<String, Object> parsePairList(Pair pair) 
	{
		HashMap<String, Object> map = new HashMap<String, Object>();
		
		if (pair.isEmpty() == false)
		{
			Object first = pair.getFirst();

			if (first instanceof Pair)
			{
				Pair firstPair = (Pair) first;
				
				String key = firstPair.first.toString();

				Object value = firstPair.rest();

				if (value instanceof Pair)
				{
					Pair valuePair = (Pair) value;
					
					value = valuePair.toString();
				}

				map.put(key, value);
			}
			
			Object rest = pair.getRest();

			if (rest instanceof Pair)
			{
				Pair restPair = (Pair) rest;
				
				Map<String, Object> restMap = SchemeEngine.parsePairList(restPair);
				
				map.putAll(restMap);
			}
		}
		
		return map;
	}

	private static Bundle bundleForPair(Pair pair) 
	{
		Bundle b = new Bundle();
		
		if (pair.isEmpty() == false)
		{
			Object first = pair.first();
			
			if (first instanceof Pair)
			{
				Pair firstPair = (Pair) first;
				
				Object firstFirst = firstPair.first();
				
				if (firstFirst instanceof String)
				{
					String key = (String) firstFirst;
					
					Object second = firstPair.second();
					
					if (second instanceof String)
						b.putString(key, second.toString());
					else if (second instanceof Pair)
					{
						Pair secondPair = (Pair) second;
						
						if (secondPair.first().toString().equalsIgnoreCase("begin"))
							b.putString(key, secondPair.toString());
						else
							b.putBundle(key, SchemeEngine.bundleForPair(secondPair));
					}
				}
			}
			
			Object rest = pair.rest();
	
			if (rest instanceof Pair && !((Pair) rest).isEmpty())
			{
				Pair restPair = (Pair) rest;
				
				Bundle restBundle = SchemeEngine.bundleForPair(restPair);
				
				b.putAll(restBundle);
				
			}
		}
		
		return b;
	}

	public void showNativeDialog(String title, String message, String confirmTitle, String cancelTitle, Pair confirmAction, Pair cancelAction)
	{
		this.showNativeDialog(title, message, confirmTitle, cancelTitle, confirmAction.toString(), cancelAction.toString());
	}

	public boolean updateConfig(Pair parameters)
	{
		Map<String, Object> paramsMap = SchemeEngine.parsePairList(parameters);
		
		return super.updateConfig(paramsMap);
	}

	public void updateWidget(Pair parameters)
	{
		Map<String, Object> paramsMap = SchemeEngine.parsePairList(parameters);
		
		Intent intent = new Intent(ManagerService.UPDATE_WIDGETS);
		
		for (String key : paramsMap.keySet())
		{
			intent.putExtra(key, paramsMap.get(key).toString());
		}

		this._context.startService(intent);
	}
	
	public void emitReading(String name, Object value)
	{
		Bundle bundle = new Bundle();
		bundle.putString("PROBE", name);
		bundle.putLong("TIMESTAMP", System.currentTimeMillis() / 1000);
		
		if (value instanceof String)
			bundle.putString(Feature.FEATURE_VALUE, value.toString());
		else if (value instanceof Double)
		{
			Double d = (Double) value;

			bundle.putDouble(Feature.FEATURE_VALUE, d.doubleValue());
		}
		else if (value instanceof Pair)
		{
			Pair pair = (Pair) value;

			Bundle b = SchemeEngine.bundleForPair(pair);

			bundle.putParcelable(Feature.FEATURE_VALUE, b);
		}
		else
		{
			Log.e("PRM", "SCHEME PLUGIN GOT UNKNOWN VALUE " + value);

			if (value != null)
				Log.e("PRM", "SCHEME PLUGIN GOT UNKNOWN CLASS " + value.getClass());
		}

		this.transmitData(bundle);
	}

	public boolean broadcastIntent(final String action, Pair extras)
	{
		return this.broadcastIntent(action, SchemeEngine.parsePairList(extras));
	}

	public boolean updateWidget(final String title, final String message, final String applicationName, final Pair launchParams, final String script)
	{
		return this.updateWidget(title, message, applicationName, SchemeEngine.parsePairList(launchParams), script);
	}

	public boolean launchApplication(String applicationName, final Pair launchParams, final String script)
	{
		return this.launchApplication(applicationName, SchemeEngine.parsePairList(launchParams), script);
	}

	public boolean showApplicationLaunchNotification(String title, String message, String applicationName, long displayWhen, final Pair launchParams, final String script)
	{
		return this.showApplicationLaunchNotification(title, message, applicationName, displayWhen, SchemeEngine.parsePairList(launchParams), script);
	}
	
	public String fetchConfig()
	{
		SchemeConfigFile config = new SchemeConfigFile(this._context);
		
		return config.toString();
	}

	public Pair dateToComponents(Date date)
	{
		String stringRep = this.formatDate(date);
		
		String year = stringRep.substring(0, 4);
		String month = stringRep.substring(4, 6);
		String day = stringRep.substring(6, 8);
		String hour = stringRep.substring(9, 11);
		String minute = stringRep.substring(11, 13);
		String second = stringRep.substring(13, 15);

		return new Pair(year, new Pair(month, new Pair(day, new Pair(hour, new Pair(minute, new Pair(second, null))))));
	}

	public Date dateFromComponents(Pair components)
	{

		String year = components.nth(0).toString();
		String month = components.nth(1).toString();
		String day = components.nth(2).toString();
		String hour = components.nth(3).toString();
		String minute = components.nth(4).toString();
		String second = components.nth(5).toString();
		
		String dateString = year + month + day + "T" + hour + minute + second;
		
		return ScheduleManager.parseString(dateString);
	}

	public Object nth(int index, Object obj)
	{
		if (obj == null)
			return null;
		else if (index < 0)
			return null;
		else if ((obj instanceof Pair) == false)
			return null;
		
		Pair list = (Pair) obj;
		
		if (index == 0)
			return list.first();
		
		return this.nth(index - 1, list.rest());
	}		
	
	public Object valueFromString(String key, String string)
	{
		Object value = super.valueFromString(key, string);
		
		if (value instanceof Map<?, ?>)
			value = this.pairForMap((Map<?, ?>) value);
		else if (value instanceof List<?>)
			value = this.pairForList((List<?>) value);
		
		return value;
	}
	
	private Pair pairForMap(Map<?, ?> map)
	{
		Pair list = Pair.EMPTY;
		
		for (Object key : map.keySet())
		{
			Object value = map.get(key.toString());
			
			if (value instanceof Map<?, ?>)
				value = this.pairForMap((Map<?, ?>) value);
			else if (value instanceof List<?>)
				value = this.pairForList((List<?>) value);
			
			list = new Pair(new Pair(key, value), list);
		}
		
		return new Pair(new Pair(Symbol.QUOTE, new Pair(list, Pair.EMPTY)), Pair.EMPTY);
	}
	
	private Pair pairForList(List<?> list)
	{
		Pair pairs = Pair.EMPTY;
		
		for (Object value : list)
		{
			if (value instanceof Map<?, ?>)
				value = this.pairForMap((Map<?, ?>) value);
			else if (value instanceof List<?>)
				value = this.pairForList((List<?>) value);

			pairs = new Pair(value, pairs);
		}

		return new Pair(Symbol.QUOTE, new Pair(pairs, Pair.EMPTY));
	}
}

