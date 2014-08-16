package com.cubic9.android.droidglove;

import com.cubic9.android.droidglove.R;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Setting preference class of DroidGlove.
 * 
 * Copyright (C) 2014, cubic9com All rights reserved.
 * This code is licensed under the BSD 3-Clause license.
 * See file LICENSE for more information.
 * 
 * @author cubic9com
 */
public class Settings extends PreferenceActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.pref);
	}
}