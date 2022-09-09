package pkg;

import com.google.cloud.tools.jib.api.ImageReference;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
    @SneakyThrows
    public static void main(String[] args) {
        ImageReference imageReference = ImageReference.parse("localhost:5000/sample:latest");
        System.out.println(imageReference.toStringWithQualifier());
        Puller puller = new Puller();
        puller.pull(imageReference);
    }
}
