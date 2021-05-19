package dev.azn9.murmurServer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

public class DockerService {

    public static void main(String[] args) {
        new DockerService().initialize();
    }

    private DockerClient dockerClient;

    public void initialize() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock")
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .build();

        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    public String createServer(int port) {
        CreateContainerCmd command = this.dockerClient.createContainerCmd("dev.azn9.murmur:latest");
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withPortBindings(new PortBinding(Ports.Binding.bindPort(port), ExposedPort.tcp(64738)))
                .withPortBindings(new PortBinding(Ports.Binding.bindPort(port), ExposedPort.udp(64738)))
                .withAutoRemove(true);

        command.withHostConfig(hostConfig);
        command.withEnv("PORT=" + port);

        CreateContainerResponse response = command.exec();

        return response.getId();
    }

    public void stopServer(String id) {
        this.dockerClient.removeContainerCmd(id).exec();
    }
}
