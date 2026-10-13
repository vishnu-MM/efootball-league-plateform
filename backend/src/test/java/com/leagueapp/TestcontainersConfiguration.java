package com.leagueapp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
	}

	/** S3-compatible storage for screenshots: the same SeaweedFS image and credentials as docker-compose.yml. */
	@Bean
	GenericContainer<?> storageContainer() throws IOException {
		byte[] identities = Files.readAllBytes(Path.of("..", "docker", "storage-s3.json"));
		return new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.48"))
			.withCopyToContainer(Transferable.of(identities), "/etc/seaweedfs/s3.json")
			.withCommand("server", "-s3", "-dir=/data", "-s3.config=/etc/seaweedfs/s3.json")
			.withExposedPorts(8333)
			.waitingFor(Wait.forHttp("/").forPort(8333).forStatusCodeMatching(code -> code < 500)
				.withStartupTimeout(Duration.ofSeconds(60)));
	}

	@Bean
	DynamicPropertyRegistrar storageProperties(GenericContainer<?> storageContainer) {
		return registry -> registry.add("app.storage.endpoint",
				() -> "http://" + storageContainer.getHost() + ":" + storageContainer.getMappedPort(8333));
	}

}
