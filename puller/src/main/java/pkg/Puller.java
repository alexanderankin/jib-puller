package pkg;

import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.cache.Cache;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.cache.CachedLayer;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.http.FailoverHttpClient;
import com.google.cloud.tools.jib.image.Layer;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.common.base.Verify;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        System.out.println();

        BuildableManifestTemplate buildableManifestTemplate = (BuildableManifestTemplate) manifestTemplate;
        for (BuildableManifestTemplate.ContentDescriptorTemplate layer : buildableManifestTemplate.getLayers()) {
            Blob b = registryClient.pullBlob(layer.getDigest(), Objects::requireNonNull, Objects::requireNonNull);
            CachedLayer cachedLayer = cache.writeCompressedLayer(b);
            System.out.println("start");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            // GZIPOutputStream gzipOutputStream = new GZIPOutputStream(bos);
            cachedLayer.getBlob().writeTo(bos);
            // gzipOutputStream.close();

            byte[] bytes = bos.toByteArray();

            GZIPInputStream zipInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes));
            TarArchiveInputStream t = new TarArchiveInputStream(zipInputStream);
            t.getNextTarEntry();
            System.out.println(new String(t.readAllBytes()));

            // ZipEntry nextEntry = zipInputStream.getNextEntry();
            ZipEntry nextEntry = null;
            System.out.println(nextEntry);
            if (nextEntry != null) {
                System.out.println(nextEntry.getName());
                System.out.println(nextEntry.getSize());
                while (zipInputStream.available() > 0) {
                    System.out.println(new String(zipInputStream.readAllBytes()));
                }
            } else {
                System.out.println("nextEntry was NULL");
            }

            System.out.println("bytes.length: " + bytes.length);
            // Files.write(Path.of("data"), bytes);
            System.out.println("end");
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

    static class ObtainBaseImageLayerStep implements Callable<Layer> {

        enum StateInTarget {
            UNKNOWN,
            EXISTING,
            MISSING
        }

        @SuppressWarnings("InlineFormatString")
        private static final String DESCRIPTION = "Pulling base image layer %s";

        @FunctionalInterface
        private interface BlobExistenceChecker {
            StateInTarget check(DescriptorDigest digest) throws IOException, RegistryException;
        }

        static ObtainBaseImageLayerStep forForcedDownload(
                BuildContext buildContext,
                ProgressEventDispatcher.Factory progressEventDispatcherFactory,
                Layer layer,
                // nullable
                RegistryClient sourceRegistryClient) {
            ObtainBaseImageLayerStep.BlobExistenceChecker noOpChecker = ignored -> StateInTarget.UNKNOWN;
            return new ObtainBaseImageLayerStep(
                    buildContext, progressEventDispatcherFactory, layer, sourceRegistryClient, noOpChecker);
        }

        static ObtainBaseImageLayerStep forSelectiveDownload(
                BuildContext buildContext,
                ProgressEventDispatcher.Factory progressEventDispatcherFactory,
                Layer layer,
                // nullable
                RegistryClient sourceRegistryClient,
                RegistryClient targetRegistryClient) {
            Verify.verify(!buildContext.isOffline());

            // TODO: also check if cross-repo blob mount is possible.
            ObtainBaseImageLayerStep.BlobExistenceChecker blobExistenceChecker =
                    digest ->
                            targetRegistryClient.checkBlob(digest).isPresent()
                                    ? StateInTarget.EXISTING
                                    : StateInTarget.MISSING;

            return new ObtainBaseImageLayerStep(
                    buildContext,
                    progressEventDispatcherFactory,
                    layer,
                    sourceRegistryClient,
                    blobExistenceChecker);
        }

        private final BuildContext buildContext;
        private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

        private final Layer layer;
        // nullable
        private final RegistryClient registryClient;
        private final ObtainBaseImageLayerStep.BlobExistenceChecker blobExistenceChecker;

        private ObtainBaseImageLayerStep(
                BuildContext buildContext,
                ProgressEventDispatcher.Factory progressEventDispatcherFactory,
                Layer layer,
                // nullable
                RegistryClient registryClient,
                ObtainBaseImageLayerStep.BlobExistenceChecker blobExistenceChecker) {
            this.buildContext = buildContext;
            this.progressEventDispatcherFactory = progressEventDispatcherFactory;
            this.layer = layer;
            this.registryClient = registryClient;
            this.blobExistenceChecker = blobExistenceChecker;
        }

        @Override
        public Layer call() throws IOException, CacheCorruptedException, RegistryException {
            EventHandlers eventHandlers = buildContext.getEventHandlers();
            DescriptorDigest layerDigest = layer.getBlobDescriptor().getDigest();
            try (ProgressEventDispatcher progressEventDispatcher =
                         progressEventDispatcherFactory.create("checking base image layer " + layerDigest, 1);
                 TimerEventDispatcher ignored =
                         new TimerEventDispatcher(eventHandlers, String.format(DESCRIPTION, layerDigest))) {

                StateInTarget stateInTarget = blobExistenceChecker.check(layerDigest);
                if (stateInTarget == StateInTarget.EXISTING) {
                    eventHandlers.dispatch(
                            LogEvent.info(
                                    "Skipping pull; BLOB already exists on target registry : "
                                            + layer.getBlobDescriptor()));
                    return layer;
                }

                Cache cache = buildContext.getBaseImageLayersCache();

                // Checks if the layer already exists in the cache.
                Optional<CachedLayer> optionalCachedLayer = cache.retrieve(layerDigest);
                if (optionalCachedLayer.isPresent()) {
                    return optionalCachedLayer.get();
                } else if (buildContext.isOffline()) {
                    throw new IOException(
                            "Cannot run Jib in offline mode; local Jib cache for base image is missing image layer "
                                    + layerDigest
                                    + ". Rerun Jib in online mode with \"-Djib.alwaysCacheBaseImage=true\" to "
                                    + "re-download the base image layers.");
                }

                @SuppressWarnings("UnnecessaryLocalVariable")
                CachedLayer cachedLayer =
                        cache.writeCompressedLayer(
                                Verify.verifyNotNull(registryClient)
                                        .pullBlob(
                                                layerDigest,
                                                Objects::requireNonNull,
                                                Objects::requireNonNull));
                return cachedLayer;
            }
        }
    }

}
