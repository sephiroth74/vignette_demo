package com.example.alessandro.vignette.log;


import android.util.Log;

import java.util.Formatter;

/**
 * Utility class used for android logging
 *
 * @author alessandro
 */
public class LoggerFactory {

	/**
	 * turning LOG_ENABLED to false will prevent any debug event with the
	 * exception of error logs
	 */
	public static boolean LOG_ENABLED = true;

	public static enum LoggerType {
		ConsoleLoggerType,
	}

	public static interface Logger {

		void log(String message);

		void info(String message);

		void warn(String message);

		void error(String message);

		/**
		 * Log using java format specifications. For more informations on how
		 * to format messages, see <a href=
		 * 'http://developer.android.com/reference/java/util/Formatter.html'>Formatter</a>
		 *
		 * @param format
		 * @param args
		 * @see Formatter
		 */
		void log(String format, Object... args);

		void info(String format, Object... args);

		void warn(String format, Object... args);

		void error(String format, Object... args);

		void verbose(String s, Object... args);
	}

	abstract static class BaseLogger implements Logger {

		String tag;

		public BaseLogger(String basetag) {
			tag = basetag;
		}

		protected StringBuilder formatArguments(Object... args) {
			StringBuilder b = new StringBuilder();
			for (Object obj : args) {
				b.append(obj + ", ");
			}
			return b;
		}
	}

	static class NullLogger extends BaseLogger {

		public NullLogger(String basetag) {
			super(basetag);
		}

		@Override
		public void log(String message) {}

		@Override
		public void info(String message) {}

		@Override
		public void warn(String message) {}

		@Override
		public void error(String message) {}

		@Override
		public void verbose(String format, Object... args) {}

		@Override
		public void log(String format, Object... args) {}

		@Override
		public void info(String format, Object... args) {}

		@Override
		public void warn(String format, Object... args) {}

		@Override
		public void error(String format, Object... args) {}

	}

	static class ConsoleLogger extends BaseLogger {

		public ConsoleLogger(String basetag) {
			super(basetag);
		}

		@Override
		public void log(String message) {
			Log.d(tag, message);
		}

		@Override
		public void info(String message) {
			Log.i(tag, message);
		}

		@Override
		public void warn(String message) {
			Log.w(tag, message);
		}

		@Override
		public void error(String message) {
			Log.e(tag, message);
		}

		@Override
		public void verbose(String format, Object... args) {
			Log.v(tag, String.format(format, args));
		}

		@Override
		public void log(String format, Object... args) {
			Log.d(tag, String.format(format, args));
		}

		@Override
		public void info(String format, Object... args) {
			Log.i(tag, String.format(format, args));
		}

		@Override
		public void warn(String format, Object... args) {
			Log.w(tag, String.format(format, args));
		}

		@Override
		public void error(String format, Object... args) {
			Log.e(tag, String.format(format, args));
		}
	}

	public static Logger getLogger(String basetag, LoggerType type) {
		if (LOG_ENABLED) {
			if (type == LoggerType.ConsoleLoggerType) {
				return new ConsoleLogger(basetag);
			}
		}
		return new NullLogger(basetag);
	}

	public static Logger getLogger(String basetag) {
		return getLogger(basetag, LoggerType.ConsoleLoggerType);
	}
}

