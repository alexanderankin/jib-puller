import java.io.File;

public class MakeSampleImage {
    public static void main(String[] args) throws Throwable {
        String image = "localhost:5000/sample:latest";
        runCommand("docker", "build", ".", "-t", image);
        runCommand("docker", "push", image);
    }

    public static void runCommand(String... command) throws Throwable {
        ProcessBuilder processBuilder = new ProcessBuilder()
                .command(command)
                .directory(new File(System.getProperty("projectDir")))
                .inheritIO()
                ;
        Process process = processBuilder.start();

        int exitCode = process.waitFor();
        System.out.println("exited with: " + exitCode);
    }
}
