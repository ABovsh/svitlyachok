package ua.abovsh.svitlo;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Periodic (~15 min, network only) and one-off fetches. */
public class FetchJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            Sync.fetchAndApply(getApplicationContext());
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
