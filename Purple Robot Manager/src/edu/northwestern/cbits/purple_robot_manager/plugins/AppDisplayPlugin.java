package edu.northwestern.cbits.purple_robot_manager.plugins;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import edu.northwestern.cbits.purple_robot_manager.RobotContentProvider;
import edu.northwestern.cbits.purple_robot_manager.activities.StartActivity;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.models.Model;
import edu.northwestern.cbits.purple_robot_manager.models.ModelManager;
import edu.northwestern.cbits.purple_robot_manager.probes.Probe;

import android.annotation.SuppressLint;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.JsonWriter;

public class AppDisplayPlugin extends OutputPlugin
{
	private long _lastUpdate = 0;

	private ArrayList<ContentValues> _valuesQueue = new ArrayList<ContentValues>();
	
	public String[] respondsTo()
	{
		String[] activeActions = { Probe.PROBE_READING, OutputPlugin.LOG_EVENT, AppDisplayPlugin.DISPLAY_MESSAGE };

		return activeActions;
	}

	@SuppressLint("NewApi")
	public void processIntent(final Intent intent)
	{
		Bundle extras = intent.getExtras();
		
		final Context context = this.getContext();

		if (AppDisplayPlugin.DISPLAY_MESSAGE.equals(intent.getAction()))
		{
			Intent displayIntent = new Intent(StartActivity.UPDATE_MESSAGE);
			displayIntent.putExtra(StartActivity.DISPLAY_MESSAGE, extras.getString("MESSAGE"));

			LocalBroadcastManager manager = LocalBroadcastManager.getInstance(context);
			manager.sendBroadcast(displayIntent);
		}
		else
		{
			final ContentValues values = new ContentValues();

			Object ts = extras.get("TIMESTAMP");
			
			if (ts instanceof Long)
				values.put("recorded", ((Long) ts).longValue());
			else if (ts instanceof Double)
				values.put("recorded", ((Double) ts).longValue());
			
			values.put("source", extras.getString("PROBE"));

			if (extras.containsKey("FROM_MODEL"))
			{
				Model m = ModelManager.getInstance(context).fetchModelByTitle(context, extras.getString("PROBE"));
				values.put("source", m.name(context));
			}
			
			try 
			{
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
				{
					try
					{
						StringWriter outputWriter = new StringWriter();
						JsonWriter jsonWriter = new JsonWriter(outputWriter);
						
						StreamingJSONUploadPlugin.writeBundle(this.getContext(), jsonWriter, extras);
	
						jsonWriter.flush();
						jsonWriter.close();
						
						values.put("value", outputWriter.toString());
					}
					catch (IOException e) 
					{
						LogManager.getInstance(this.getContext()).logException(e);
						
						JSONObject json = OutputPlugin.jsonForBundle(extras);
						values.put("value",  json.toString());
					}
				}
				else
				{
					JSONObject json = OutputPlugin.jsonForBundle(extras);
					values.put("value",  json.toString());
				}
			}
			catch (JSONException e) 
			{
     			LogManager.getInstance(context).logException(e);
			}
			
			ContentValues toRemove = null;
			
			synchronized(this._valuesQueue)
			{
				do
				{
					toRemove = null;
					
					for (ContentValues check : this._valuesQueue)
					{
						if (check.getAsString("source").equals(values.getAsString("source")))
							toRemove = check;
					}
					
					if (toRemove != null)
						this._valuesQueue.remove(toRemove);
				}
				while (toRemove != null);

				this._valuesQueue.add(values);
			}

			if (System.currentTimeMillis() - this._lastUpdate > 1000)
			{
				this._lastUpdate = System.currentTimeMillis();
				
				final ArrayList<ContentValues> toUpdate = new ArrayList<ContentValues>();
				
				while (this._valuesQueue.size() > 0)
				{
					toUpdate.add(this._valuesQueue.remove(0));
				}
				
				Handler mainHandler = new Handler(context.getMainLooper());
				
				mainHandler.post(new Runnable()
				{
					public void run() 
					{
						String where = "source = ?";
						
						ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

						for (ContentValues value: toUpdate)
						{
							String[] whereArgs = { value.getAsString("source") };

							ContentProviderOperation.Builder update = ContentProviderOperation.newUpdate(RobotContentProvider.RECENT_PROBE_VALUES).withSelection(where, whereArgs).withValues(value);
							
							ops.add(update.build());
						}

						try 
						{
							context.getContentResolver().applyBatch(RobotContentProvider.AUTHORITY, ops);
						}
						catch (RemoteException e) 
						{
		         			LogManager.getInstance(context).logException(e);
						} 
						catch (OperationApplicationException e) 
						{
		         			LogManager.getInstance(context).logException(e);
						}

						context.getContentResolver().notifyChange(RobotContentProvider.RECENT_PROBE_VALUES, null);
					}
				});
			}
		}
	}
}
