package ua.abovsh.svitlo;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.HashMap;
import java.util.Map;

/** Each running job owns its cancellable request and completes at most once. */
public class FetchJob extends JobService {
    private final Map<Integer, Store.Request> running = new HashMap<>();
    @Override public boolean onStartJob(JobParameters params) {
        Store.Request request = new Store.Request();
        synchronized (running) { running.put(params.getJobId(), request); }
        new Thread(() -> {
            boolean retry = true;
            try { retry = !Sync.refreshInBackground(getApplicationContext(), request); }
            finally {
                synchronized (running) {
                    if (running.get(params.getJobId()) == request) {
                        running.remove(params.getJobId());
                        jobFinished(params, retry);
                    }
                }
            }
        }, "schedule-job").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        Store.Request request;
        synchronized (running) { request = running.remove(params.getJobId()); }
        if (request != null) request.cancel();
        return true;
    }
}
