package net.fabricmc.loom.repository;

import net.gudenau.lib.unsafe.Unsafe;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.DefaultUrlArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import user11681.reflect.Classes;

import net.fabricmc.loom.LoomExtension;

public class LoomMavenArtifactRepository extends DefaultMavenArtifactRepository {
    public static final long classPointer = Classes.getClassPointer(LoomMavenArtifactRepository.class);

    public LoomMavenArtifactRepository(
        FileResolver fileResolver,
        RepositoryTransportFactory transportFactory,
        LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
        InstantiatorFactory instantiatorFactory,
        FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
        MetaDataParser<MutableMavenModuleResolveMetadata> pomParser,
        GradleModuleMetadataParser metadataParser,
        AuthenticationContainer authenticationContainer,
        FileStore<String> resourcesFileStore,
        FileResourceRepository fileResourceRepository,
        MavenMutableModuleMetadataFactory metadataFactory,
        IsolatableFactory isolatableFactory,
        ObjectFactory objectFactory,
        DefaultUrlArtifactRepository.Factory urlArtifactRepositoryFactory,
        ChecksumService checksumService,
        ProviderFactory providerFactory,
        FeaturePreviews featurePreviews) {
        super(
            fileResolver,
            transportFactory,
            locallyAvailableResourceFinder,
            instantiatorFactory,
            artifactFileStore,
            pomParser,
            metadataParser,
            authenticationContainer,
            resourcesFileStore,
            fileResourceRepository,
            metadataFactory,
            isolatableFactory,
            objectFactory,
            urlArtifactRepositoryFactory,
            checksumService,
            providerFactory,
            featurePreviews
        );
    }

    @Override
    public void setName(String name) {
        super.setName(name);

        String resolvedURL = LoomExtension.repository(name);

        if (resolvedURL != null) {
            this.setUrl(resolvedURL);
        }
    }
}
