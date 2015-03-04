package eu.siacs.conversations;

import android.app.Application;
import android.content.Context;


public class ConversationsApplication extends Application {

	private static Context context = null;

	public static Context getContext() {
		return context;
	}

	@Override
	public void onCreate(){
		super.onCreate();

		ConversationsApplication.context = getApplicationContext();
	}

}
