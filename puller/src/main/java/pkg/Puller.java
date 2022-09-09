package pkg;

import com.google.cloud.tools.jib.api.ImageReference;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Puller {
    void pull(ImageReference imageReference) {
        log.debug("pulling {}", imageReference);
    }
}
