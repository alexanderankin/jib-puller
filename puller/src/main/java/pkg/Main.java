package pkg;

import com.google.cloud.tools.jib.api.ImageReference;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
    @SneakyThrows
    public static void main(String[] args) {
        ImageReference image = ImageReference.parse("localhost:5000/image");
        Puller puller = new Puller();
        puller.pull(image);
    }
}
