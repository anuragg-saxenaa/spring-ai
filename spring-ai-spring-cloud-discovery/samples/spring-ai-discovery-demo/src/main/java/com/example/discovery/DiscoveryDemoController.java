package com.example.discovery;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.ai.discovery.agent.A2aRequest;
import org.springframework.ai.discovery.agent.A2aResponse;
import org.springframework.ai.discovery.agent.AgentService;
import org.springframework.ai.discovery.mcp.McpClientFactory;
import org.springframework.ai.mcp.annotation.spring.EnableMcpClient;
import org.springframework.ai.mcp.annotation.spring.McpToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Demo REST controller showing how to use the service discovery integration.
 *
 * <p>
 * This application acts as both: - An MCP client that discovers MCP servers via Eureka
 * - An A2A agent that can be discovered and called by other agents
 *
 * <p>
 * MCP servers register with metadata type=mcp-server. A2A agents register with metadata
 * type=a2a-agent.
 */
@RestController
@RequestMapping("/demo")
@Description("Demo controller for Spring AI Service Discovery integration")
public class DiscoveryDemoController {

	private static final Logger logger = LoggerFactory.getLogger(DiscoveryDemoController.class);

	private final McpClientFactory mcpClientFactory;

	private final AgentService agentService;

	private final List<McpSyncClient> mcpClients;

	public DiscoveryDemoController(
			@Autowired(required = false) McpClientFactory mcpClientFactory,
			@Autowired(required = false) AgentService agentService,
			@Autowired(required = false) List<McpSyncClient> mcpClients) {
		this.mcpClientFactory = mcpClientFactory;
		this.agentService = agentService;
		this.mcpClients = mcpClients;
	}

	/**
	 * List all MCP server services discovered via the service registry.
	 */
	@GetMapping("/mcp/servers")
	public List<String> listMcpServers() {
		if (this.mcpClientFactory == null) {
			return List.of("Service discovery not available");
		}
		return this.mcpClientFactory.discoverMcpServerServices();
	}

	/**
	 * List all A2A agent services discovered via the service registry.
	 */
	@GetMapping("/agents")
	public List<String> listAgents() {
		if (this.agentService == null) {
			return List.of("Service discovery not available");
		}
		return this.agentService.discoverAgentServices();
	}

	/**
	 * Call a remote A2A agent by service name.
	 * <p>
	 * Example request body: { "method": "invoke", "params": { "prompt": "What's the
	 * weather?" }, "id": "1" }
	 */
	@PostMapping("/agents/call")
	public A2aResponse callAgent(@RequestBody A2aRequest request) {
		if (this.agentService == null) {
			throw new IllegalStateException("AgentService is not available");
		}
		logger.info("Calling agent via service registry: {}", request.getMethod());
		return this.agentService.callAgent(request.getMethod(), request.getParams());
	}

	/**
	 * Refresh the service discovery cache and re-discover all services.
	 */
	@PostMapping("/refresh")
	public String refresh() {
		if (this.mcpClientFactory != null) {
			this.mcpClientFactory.refresh();
		}
		if (this.agentService != null) {
			this.agentService.refresh();
		}
		return "Service discovery cache refreshed";
	}

	/**
	 * Example tool exposed by this agent (so other agents can call it).
	 */
	@Tool(description = "Get the current status of the discovery integration")
	public String getStatus() {
		int mcpCount = this.mcpClients != null ? this.mcpClients.size() : 0;
		return "Discovery integration active. MCP clients: " + mcpCount;
	}

}
