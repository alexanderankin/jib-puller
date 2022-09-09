package pkg;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.tar.TarStreamBuilder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
public class Puller {
    @SneakyThrows
    void pull(ImageReference imageReference) {
        log.debug("pulling {}", imageReference);
        RegistryClient registryClient =
                new RegistryClientFactoryProperties()
                        .setEventHandlers(EventHandlers.builder().build())
                        .setServerUrl(imageReference.getRegistry())
                        .setImageName(imageReference.getRepository())
                        .setSourceImageName(null)
                        .setHttpClient(new FailoverHttpClient(true,
                                true,
                                e -> log.trace("http client log event: {}", e)))
                        .build();

        ManifestAndDigest<ManifestTemplate> manifest =
                registryClient.pullManifest(imageReference.getTag().orElse("latest"));

        DescriptorDigest descriptorDigest = manifest.getDigest();
        ManifestTemplate manifestTemplate = manifest.getManifest();
        System.out.println();

        BuildableManifestTemplate buildableManifestTemplate = (BuildableManifestTemplate) manifestTemplate;
        for (BuildableManifestTemplate.ContentDescriptorTemplate layer : buildableManifestTemplate.getLayers()) {
            Blob b = registryClient.pullBlob(layer.getDigest(), Objects::requireNonNull, Objects::requireNonNull);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            b.writeTo(byteArrayOutputStream);
            byte[] bytes = byteArrayOutputStream.toByteArray();
            System.out.println(Arrays.toString(bytes));

            // doesn't work because actually it is gzipped
            TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(new ByteArrayInputStream(bytes));
            TarArchiveEntry entry;
            Files.write(Path.of("data"), bytes);
            while ((entry = tarArchiveInputStream.getNextTarEntry()) != null) {
                System.out.println(entry);
            }
        }
        ;
        //
        //
        // Blob blob = registryClient.pullBlob(manifest.getDigest(), l -> {
        // }, l -> {
        // });
        //
        // Image image = Image.builder(V22ManifestTemplate.class)
        //         .addLayer(layerOf(blob))
        //         .build();
        // new ImageTarball(image, imageReference, null);
    }
}
