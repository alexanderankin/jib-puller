package pkg;

import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.registry.RegistryClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
    @SneakyThrows
    public static void main(String[] args) {
        ImageReference imageReference = ImageReference.parse("localhost:5000/sample:latest");
        System.out.println(imageReference.toStringWithQualifier());

        RegistryClientFactoryProperties props = new RegistryClientFactoryProperties()
                .setEventHandlers(EventHandlers.builder().build())
                .setServerUrl(imageReference.getRegistry())
                .setImageName(imageReference.getRepository())
                .setSourceImageName(null)
                .setHttpClient(new FailoverHttpClient(true,
                        true,
                        e -> log.trace("http client log event: {}", e)))
                .setUsername(null)
                .setPassword(null);

        RegistryClient registryClient = props.build();
        if (props.hasCredentials()) {
            boolean pulled = registryClient.doPullBearerAuth();
            if (!pulled) {
                throw new IllegalStateException("couldn't pull bearer even though have credentials");
            }
        }

        Puller puller = new Puller(registryClient);
        puller.pull(imageReference, "abc/sample.txt", "data");
    }
}
