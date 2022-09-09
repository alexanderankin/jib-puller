package pkg;

import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.registry.RegistryClient;
import lombok.Data;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Data
public class RegistryClientFactoryProperties {
    EventHandlers eventHandlers;
    String serverUrl;
    String imageName;
    String sourceImageName;
    FailoverHttpClient httpClient;

    RegistryClient build() {
        return RegistryClient.factory(eventHandlers,
                        serverUrl,
                        imageName,
                        sourceImageName,
                        httpClient)
                .newRegistryClient();
    }
}
