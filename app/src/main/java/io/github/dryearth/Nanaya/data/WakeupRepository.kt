package io.github.dryearth.Nanaya.data

import android.content.Context
import io.github.dryearth.Nanaya.core.Shell

data class WakeupSource(
    val pkg: String,
    val label: String,
    val jobs: Int,
    val alarms: Int,
)

class WakeupRepository(private val context: Context, private val apps: AppRepository) {

    fun scan(): List<WakeupSource> {
        if (!Shell.isPrivileged()) return emptyList()
        val labels = apps.list().associate { it.pkg to it.label }
        val jobs = HashMap<String, Int>()
        val alarms = HashMap<String, Int>()

        val jobText = Shell.run("dumpsys jobscheduler", 25_000).text
        Regex("""JOB #u\d+a\d+/\d+:\s+\S+\s+([^\s/]+)/""").findAll(jobText).forEach {
            val pkg = it.groupValues[1]
            jobs[pkg] = (jobs[pkg] ?: 0) + 1
        }

        val alarmText = Shell.run("dumpsys alarm", 25_000).text
        Regex("""package=([A-Za-z][\w.]+)""").findAll(alarmText).forEach {
            val pkg = it.groupValues[1]
            alarms[pkg] = (alarms[pkg] ?: 0) + 1
        }

        return (jobs.keys + alarms.keys).map { pkg ->
            WakeupSource(pkg, labels[pkg] ?: pkg, jobs[pkg] ?: 0, alarms[pkg] ?: 0)
        }.sortedByDescending { it.jobs + it.alarms }.take(30)
    }
}
