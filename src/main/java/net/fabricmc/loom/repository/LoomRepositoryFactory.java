package net.fabricmc.loom.repository;

import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.repositories.DefaultBaseRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultUrlArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
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

public class LoomRepositoryFactory extends DefaultBaseRepositoryFactory {
	public static final long classPointer = Classes.getClassPointer(LoomRepositoryFactory.class);

    public LoomRepositoryFactory(
        LocalMavenRepositoryLocator localMavenRepositoryLocator,
        FileResolver fileResolver,
        FileCollectionFactory fileCollectionFactory,
        RepositoryTransportFactory transportFactory,
        LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
        FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
        FileStore<String> externalResourcesFileStore,
        MetaDataParser<MutableMavenModuleResolveMetadata> pomParser,
        GradleModuleMetadataParser metadataParser,
        AuthenticationSchemeRegistry authenticationSchemeRegistry,
        IvyContextManager ivyContextManager,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        InstantiatorFactory instantiatorFactory,
        FileResourceRepository fileResourceRepository,
        MavenMutableModuleMetadataFactory mavenMetadataFactory,
        IvyMutableModuleMetadataFactory ivyMetadataFactory,
        IsolatableFactory isolatableFactory,
        ObjectFactory objectFactory,
        CollectionCallbackActionDecorator callbackActionDecorator,
        DefaultUrlArtifactRepository.Factory urlArtifactRepositoryFactory,
        ChecksumService checksumService,
        ProviderFactory providerFactory,
        FeaturePreviews featurePreviews) {
        super(
            localMavenRepositoryLocator,
            fileResolver,
            fileCollectionFactory,
            transportFactory,
            locallyAvailableResourceFinder,
            artifactFileStore,
            externalResourcesFileStore,
            pomParser,
            metadataParser,
            authenticationSchemeRegistry,
            ivyContextManager,
            moduleIdentifierFactory,
            instantiatorFactory,
            fileResourceRepository,
            mavenMetadataFactory,
            ivyMetadataFactory,
            isolatableFactory,
            objectFactory,
            callbackActionDecorator,
            urlArtifactRepositoryFactory,
            checksumService,
            providerFactory,
            featurePreviews
        );
    }

    @Override
    public MavenArtifactRepository createMavenRepository() {
        return Classes.staticCast(super.createMavenRepository(), LoomMavenArtifactRepository.classPointer);
    }
}
