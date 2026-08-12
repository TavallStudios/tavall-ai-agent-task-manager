package org.tavall.ai.agent.recovery;

import org.tavall.ai.agent.role.TavallAIAgentRole;
import org.tavall.ai.agent.role.TavallAIAgentRoleCapability;
import org.tavall.ai.agent.role.TavallAIAgentRoleInstructions;
import org.tavall.ai.agent.role.TavallAIAgentRoleKind;
import org.tavall.ai.agent.role.TavallAIAgentRoleProvider;

import java.util.Set;

/** Restricted health/recovery role for peer supervision and off-production production recovery. */
public final class RecoveryAgentRoleProvider implements TavallAIAgentRoleProvider {
    public static final String ROLE_ID = "recovery";

    @Override
    public TavallAIAgentRole role() {
        return new TavallAIAgentRole(
                ROLE_ID,
                "Inspects peer health and requests bounded Tavall Cloud recovery operations.",
                TavallAIAgentRoleKind.CONTROL,
                TavallAIAgentRoleInstructions.load(RecoveryAgentRoleProvider.class, "ROLE.md"),
                Set.of(
                        "cloud_list_nodes",
                        "cloud_inspect_node",
                        "cloud_inspect_node_health"
                ),
                Set.of(
                        "cloud_inspect_service_logs",
                        "cloud_inspect_network_health",
                        "cloud_inspect_storage_health",
                        "cloud_request_node_agent_restart",
                        "cloud_request_service_recovery",
                        "cloud_request_storage_repair",
                        "cloud_request_node_drain",
                        "cloud_request_signed_release_rollback",
                        "cloud_request_provider_recovery"
                ),
                Set.of(
                        TavallAIAgentRoleCapability.FUNCTION_DISCOVERY,
                        TavallAIAgentRoleCapability.PEER_SUPERVISION,
                        TavallAIAgentRoleCapability.SYSTEM_HEALTH_READ,
                        TavallAIAgentRoleCapability.RECOVERY_PLANNING,
                        TavallAIAgentRoleCapability.RECOVERY_ACTION_REQUEST
                ),
                false,
                false
        );
    }
}
