fun main() {
    val regex = Regex("""(\d+)\s+bytes from\s+(\S+?)(?:\s+\(([^)]+)\))?:.*?icmp_seq=(\d+).*?ttl=(\d+).*?time=([\d.]+)\s*ms""")
    val lines = listOf(
        "64 bytes from ord37s34-in-f14.1e100.net (142.250.190.46): icmp_seq=1 ttl=115 time=12.2 ms",
        "64 bytes from 8.8.8.8: icmp_seq=2 ttl=115 time=11.5 ms",
        "64 bytes from 2607:f8b0:4009:815::200e: icmp_seq=3 ttl=115 time=11.6 ms",
        "64 bytes from maa03s35-in-x0e.1e100.net (2607:f8b0:4009:815::200e): icmp_seq=4 ttl=115 time=11.4 ms"
    )
    for (line in lines) {
        val match = regex.find(line)
        if (match != null) {
            println("Matched: ${match.groupValues}")
        } else {
            println("No match for: $line")
        }
    }
}
