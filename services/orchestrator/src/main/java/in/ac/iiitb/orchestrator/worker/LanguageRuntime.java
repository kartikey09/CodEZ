package in.ac.iiitb.orchestrator.worker;

/**
 * Maps a language slug to its Judge0 CE language id and its resource budget. The CPU budget is
 * the problem's time limit scaled per language (interpreters/VMs are slower): C/C++ x1, Java x2
 * plus a 1 s JVM-warmup allowance, Python x3. Wall and memory are derived with headroom.
 *
 * Language ids are CE 1.13.x; confirm on the instance with GET /languages.
 */
public enum LanguageRuntime {

    C("c", 50, 1.0, 0.0),
    CPP("cpp", 54, 1.0, 0.0),
    JAVA("java", 62, 2.0, 1.0),
    PYTHON("python", 71, 3.0, 0.0);

    private final String slug;
    private final int judge0Id;
    private final double cpuMultiplier;
    private final double extraCpuSeconds;

    LanguageRuntime(String slug, int judge0Id, double cpuMultiplier, double extraCpuSeconds) {
        this.slug = slug;    //the language identifier string, "c" "c++" "python" "java"
        this.judge0Id = judge0Id;   //the language id
        this.cpuMultiplier = cpuMultiplier; //x times the CPU time because some languages are slower
        this.extraCpuSeconds = extraCpuSeconds; //1 sec for JVM warm up
    }

    public static LanguageRuntime fromSlug(String slug) {
        for (LanguageRuntime r : values()) {
            if (r.slug.equals(slug)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unknown language slug: " + slug);
    }

    public int judge0Id() {
        return judge0Id;
    }

    /** CPU limit in seconds for a problem whose base limit is timeLimitMs. */
    public double cpuLimitSeconds(int timeLimitMs) {
        return (timeLimitMs / 1000.0) * cpuMultiplier + extraCpuSeconds;
    }

    /** Wall limit in seconds — generous headroom over CPU so scheduling jitter doesn't false-TLE. */
    public double wallLimitSeconds(int timeLimitMs) {
        return cpuLimitSeconds(timeLimitMs) * 2.0 + 1.0;
    }

    /** Judge0 memory limit is in kilobytes. */
    public int memoryLimitKb(int memoryLimitMb) {
        return memoryLimitMb * 1024;
    }
}
