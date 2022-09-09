package pkg;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;
import com.google.cloud.tools.jib.registry.RegistryClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

@Slf4j
public class Puller {
    @SneakyThrows
    void pull(ImageReference imageReference) {
        log.debug("pulling {}", imageReference);
        Cache cache = Cache.withDirectory(Path.of("/tmp/puller"));

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
        log.trace("descriptorDigest: {}", descriptorDigest);

        BuildableManifestTemplate buildableManifestTemplate = (BuildableManifestTemplate) manifestTemplate;
        for (BuildableManifestTemplate.ContentDescriptorTemplate layer : buildableManifestTemplate.getLayers()) {
            Blob b = registryClient.pullBlob(layer.getDigest(), Objects::requireNonNull, Objects::requireNonNull);
            CachedLayer cachedLayer = cache.writeCompressedLayer(b);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            cachedLayer.getBlob().writeTo(bos);
            byte[] bytes = bos.toByteArray();

            GZIPInputStream zipInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes));
            TarArchiveInputStream t = new TarArchiveInputStream(zipInputStream);
            t.getNextTarEntry();
            byte[] entryBytes = t.readAllBytes();

            // for debugging purposes
            log.debug("bytes.length: " + bytes.length);
            String contents = new String(entryBytes);
            log.trace(contents);

            Files.write(Path.of("data"), entryBytes);
            log.debug(System.lineSeparator() + new String(Runtime.getRuntime().exec("md5sum data make-sample-image/sample.txt").getInputStream().readAllBytes()));
        }
    }
}
