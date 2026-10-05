package cl.negociospyme.blockpets;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class PetReminderService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        // Four small local records only; no network or long-running work.
        PetNotifications.check(this);
        return false;
    }
    @Override public boolean onStopJob(JobParameters params) { return false; }
}
