package com.android.server;

import android.os.UEventObserver;
import android.util.EventLog;
import android.util.Slog;
import android.content.Context;
import android.content.Intent;
import android.os.ITouchSensorService;
import java.text.SimpleDateFormat;
import android.util.TimeUtils;
import java.util.Date;

 public  class TouchSensorService extends ITouchSensorService.Stub{ // ref:  BatteryService.java

 private static final String TAG="TouchsensorService";
 
 private static final String TP_UEVENT_MATCH = "DEVPATH=/devices/virtual/switch/touchsensor";
  private static final String TP_STATE_PATH = "/sys/class/switch/touchsensor/state";
  private static final String TP_NAME_PATH = "/sys/class/switch/touchsensor/name";
 private static   Context Tcontext;
 TouchSensorService ()
 {
 				TSensorObserver.startObserving(TP_UEVENT_MATCH);
  } 
  TouchSensorService (Context context)
 {
 				//super(context);
 				this.Tcontext=context;
 				TSensorObserver.startObserving(TP_UEVENT_MATCH);
  } 

 public void touchsensor_init()
  {
  	 Slog.i(TAG, "touchsensor   init-----------" ); 
  	return ;
  }
      
 private final UEventObserver TSensorObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event)
         {
         	 // Intent intent = new Intent("android.intent.extra.TouchSensor");
		    
					 Intent intent = new Intent("TouchSensor");

	SimpleDateFormat   formatter   =   new   SimpleDateFormat("yyyy-MM-dd   hh:mm:ss");
	Date   						 curDate   =   new   Date(System.currentTimeMillis());  
	String   					str   =   formatter.format(curDate);
		
             boolean touch = "t_head".equals (event.get("SWITCH_NAME")) ? true : false;
            
            if(touch)
            {
               intent.putExtra("android.intent.extra.Touch", "t_head");		//wakeup
	      intent.putExtra("android.intent.extra.Wakeup","t_head_wake");
	      Tcontext.sendBroadcast(intent);
		Slog.i(TAG, "t_head " + str); 
            }
//-----------------cpu:mt8163--------------------
	touch = "5micon".equals (event.get("SWITCH_NAME")) ? true : false;		   
	   if(touch)
	   {
		  intent.putExtra("android.intent.extra.Touch", "5micon");
		 Tcontext.sendBroadcast(intent);
  		 Slog.i(TAG, "5mic " + str); 
	   }
	   touch = "5micoff".equals (event.get("SWITCH_NAME")) ? true : false;		   
	   if(touch)
	   {
		  intent.putExtra("android.intent.extra.Touch", "5micoff");
		 Tcontext.sendBroadcast(intent);
  		 Slog.i(TAG, "5mic " + str); 
	   }

	   touch = "sos".equals (event.get("SWITCH_NAME")) ? true : false;		   
	   if(touch)
	   {
		  intent.putExtra("android.intent.extra.Touch", "sos");
		 Tcontext.sendBroadcast(intent);
  		 Slog.i(TAG, "sos " + str); 
	   }
//---------------------end---------------------

//-----------------y50-----------------------------------
	  touch = "left".equals (event.get("SWITCH_NAME")) ? true : false;
            
            if(touch)
            {
               intent.putExtra("android.intent.extra.Touch", "t_left");
	      Tcontext.sendBroadcast(intent);
		Slog.i(TAG, "t_left " + str); 
            }
	   touch = "back".equals (event.get("SWITCH_NAME")) ? true : false;
            if(touch)
            {
               intent.putExtra("android.intent.extra.Touch", "t_back");
		Tcontext.sendBroadcast(intent);
		Slog.i(TAG, "t_back " + str); 
            }
            touch = "right".equals (event.get("SWITCH_NAME")) ? true : false;
            if(touch)
            {
               intent.putExtra("android.intent.extra.Touch", "t_right");
	     Tcontext.sendBroadcast(intent);
		 Slog.i(TAG, "t_right " + str); 
            }

	  touch = "head".equals (event.get("SWITCH_NAME")) ? true : false;
            if(touch)
            {
               intent.putExtra("android.intent.extra.Touch", "t_head");
			   intent.putExtra("android.intent.extra.Wakeup","t_head_wake");
	      Tcontext.sendBroadcast(intent);
		  Slog.i(TAG, "t_head " + str); 
            }
	     touch = "dance".equals (event.get("SWITCH_NAME")) ? true : false;
	 if(touch)
             {
               intent.putExtra("android.intent.extra.Touch", "dance");//dance
			   Tcontext.sendBroadcast(intent);
			   Slog.i(TAG, "dance" + str); 
            }
//------------------------end-------------------------------			
            touch = "yyd5".equals (event.get("SWITCH_NAME")) ? true : false;
            if(touch)
            {
               intent.putExtra("android.intent.extra.Touch", "yyd5"); //voldown
	     Tcontext.sendBroadcast(intent);
		 Slog.i(TAG, "yyd5 " + str); 
            }

	touch = "yyd6".equals (event.get("SWITCH_NAME")) ? true : false;

	if(touch)
            {
               intent.putExtra("android.intent.extra.Touch", "yyd6");//volup
	      Tcontext.sendBroadcast(intent);
		  Slog.i(TAG, "yyd6 " + str); 
            }

	   touch = "yyd3".equals (event.get("SWITCH_NAME")) ? true : false;
				
             if(touch)
             {
               intent.putExtra("android.intent.extra.Touch", "yyd3");//entocn
	     Tcontext.sendBroadcast(intent);
		 Slog.i(TAG, "yyd3 " + str); 
            }

	  touch = "yyd4".equals (event.get("SWITCH_NAME")) ? true : false;
				
             if(touch)
             {
               intent.putExtra("android.intent.extra.Touch", "yyd4");//cntoen
	     Tcontext.sendBroadcast(intent);
		 Slog.i(TAG, "yyd4 " + str); 
            }
		 touch = "yyd2".equals (event.get("SWITCH_NAME")) ? true : false;
			 if(touch)
             {
               intent.putExtra("android.intent.extra.Touch", "yyd2");//dance
			   Tcontext.sendBroadcast(intent);
			   Slog.i(TAG, "yyd2 " + str); 
            }
		 touch = "yyd7".equals (event.get("SWITCH_NAME")) ? true : false;
			 if(touch)
             {
               intent.putExtra("android.intent.extra.Touch", "yyd7");//
			   Tcontext.sendBroadcast(intent);
			   Slog.i(TAG, "yyd7 " + str); 
            }
	    
        	Slog.i(TAG, "touchsersor " + event.get("SWITCH_NAME")+str); 
         }
  };
  
  
}
