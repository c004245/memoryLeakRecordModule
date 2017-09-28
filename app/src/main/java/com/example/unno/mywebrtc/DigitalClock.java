package com.example.unno.mywebrtc;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by chohyunwook on 2017-01-11.
 */

public class DigitalClock {
    public interface TickListener {
        public void tick(String dateTime);
    }

    public static String DATE_TIME_FORMAT = "yy-MM-dd HH:mm";

    private static Calendar toDay = Calendar.getInstance();

    private static TickListener mTickListener = null;

    private static Integer simpleLock = new Integer(1);
    private static SimpleDateFormat sdf = null;

    private static Timer displayTimer = null;
    private static TimerTask displayTimerTask = null;

    private static DigitalClock mClock = null;

    private static List<TimeEventListener> daily = new ArrayList<TimeEventListener>();

    private static HashMap<Long, ArrayList<TimeEventListener>> timeObserver = null;

    public static void createClock(TickListener listener) {
        if (mClock == null) {
            mClock = new DigitalClock(listener);
            applyDateTimeFormat(DATE_TIME_FORMAT);
        }
    }

    public static void startClock() {
        if (displayTimer != null) {
            createTask();
            displayTimer.schedule(displayTimerTask, 0, 1000);
            timeObserver = new HashMap<Long, ArrayList<TimeEventListener>>();
        }
    }

    public static void stopClock() {
        if (displayTimer != null) {
            displayTimerTask.cancel();
        }

        if (timeObserver != null)
            timeObserver = null;
    }

    public static void applyDateTimeFormat(final String format) {
        synchronized (simpleLock) {
            try {
                if (sdf != null)
                    sdf.applyPattern(format);
                else
                    sdf = new SimpleDateFormat(format);

            } catch (IllegalArgumentException e) {
                if (sdf != null)
                    sdf.applyPattern(DATE_TIME_FORMAT);
                else
                    sdf = new SimpleDateFormat(DATE_TIME_FORMAT);
            }
        }
    }

    public static void applyDateTimeFormat(final String format, Locale locale) {
        synchronized (simpleLock) {
            try {
                if (sdf != null)
                    sdf = null;
                sdf = new SimpleDateFormat(format, locale);
            } catch (IllegalArgumentException e) {
                sdf = new SimpleDateFormat(DATE_TIME_FORMAT, locale);
            }
        }
    }

    public static void addDailyObserver(TimeEventListener observer) {
        daily.add(observer);
    }

    public static void addObserver(TimeEventListener observer, int hour, int minute) {
        long millis = (hour * 3600000) + (minute * 60000);

        ArrayList<TimeEventListener> observers = null;
        if (!timeObserver.containsKey(millis)) {
            observers = new ArrayList<TimeEventListener>();
            timeObserver.put(millis, observers);
        } else
            observers = timeObserver.get(millis);

        observers.add(observer);
    }

    public static Calendar getToDay() {
        return toDay;
    }

    private DigitalClock(TickListener listener) {
        createToDay();

        mTickListener = listener;

        displayTimer = new Timer();
    }

    private static void createTask() {
        displayTimerTask = new TimerTask() {

            private int second = Calendar.getInstance(Locale.KOREA).get(Calendar.SECOND);

            @Override
            public void run() {
                Calendar nowTime = Calendar.getInstance();

                synchronized (simpleLock) {
                    mTickListener.tick(sdf.format(nowTime.getTime()));
                }

                if (++second > 60) {
                    second = 1;

                    if (toDay.before(Calendar.getInstance(Locale.KOREA))) {
                        for (TimeEventListener listener : daily)
                            listener.alarm();

                        daily.clear();
                        createToDay();
                    }

                    long toDayMilli = toDay.getTimeInMillis();
                    for (Long millis : timeObserver.keySet()) {
                        Date date = new Date(toDayMilli + millis);
                        Date now = nowTime.getTime();

                        if (now.getHours() == date.getHours()
                                && now.getMinutes() == date.getMinutes())
                            for (TimeEventListener listener : timeObserver.get(millis))
                                listener.alarm();
                        ;
                    }
                }
            }
        };
    }
        private static void createToDay() {
            toDay = Calendar.getInstance(Locale.KOREA);
            toDay.set(Calendar.HOUR_OF_DAY, 0);
            toDay.set(Calendar.MINUTE, 0);
            toDay.set(Calendar.SECOND, 0);
        }
}
