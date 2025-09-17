package com.lansoftprogramming.runeSequence;

import com.lansoftprogramming.runeSequence.config.AppSettings;
import com.lansoftprogramming.runeSequence.config.ConfigManager;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Main {
	private static final String APP_NAME = "RuneSequence";
	private static final String VERSION = "1.0.0";
	private static final String APP_DIR = "RuneSequence";
	private static final String SETTINGS_FILE = "settings.json";
	private static final String ROTATIONS_FILE = "rotations.json";
	private static final String ABILITIES_FILE = "abilities.json";

	private static AppSettings settings;
	private static TemplateCache templateCache;
	//logging
	private static final Level LOG_LEVEL = Level.ALL;
	private static Logger log = Logger.getLogger(Main.class.getName());

	static {
		ConsoleHandler handler = new ConsoleHandler();
		handler.setLevel(LOG_LEVEL);
		log.addHandler(handler);
		log.setLevel(LOG_LEVEL);
	}

	//	private static final String LOG_FILE = "RuneSequence.log";
//	private static final String LOG_DIR = "logs";
//	private static final String LOG_FORMAT = "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$-6s [%2$s] %5$s %6$s%n";
//	private static final String LOG_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
//	private static final String LOG_LEVEL_DEBUG = "DEBUG";
	public static void main(String[] args) {

		System.out.println("HEllo world");
		//get data from config file
		populateSettings();
		populateTemplateCache();
	}

	public static void populateTemplateCache() {
		templateCache = new TemplateCache(APP_DIR);
		//sleep for 10 seconds to let the template load
		try {
			Thread.sleep(1000);
			log.finest("Template loaded: " + templateCache.getCacheSize());

		} catch (InterruptedException e) {
			log.severe("Failed to sleep");
			throw new RuntimeException(e);
		}
	}


	//populate settings
	public static void populateSettings() {
		ConfigManager config = new ConfigManager();
		try {
			config.initialize();
		} catch (IOException e) {
			log.severe("Failed to load settings");
			throw new RuntimeException(e);
		}
		settings = config.getSettings();
		//log settings
		log.fine("Settings loaded, last updated: " + settings.getUpdated());
	}


}
