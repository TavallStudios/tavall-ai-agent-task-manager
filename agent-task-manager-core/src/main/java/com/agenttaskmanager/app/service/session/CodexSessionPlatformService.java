package com.agenttaskmanager.app.service.session;

import com.agenttaskmanager.app.model.session.CodexSessionApiModels.AttachSessionRequest;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.CreateSessionRequest;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.ResumeSessionRequest;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.SessionDetail;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.SessionSummary;
import com.agenttaskmanager.app.model.session.CodexSessionApiModels.SubmitTurnRequest;
import com.agenttaskmanager.app.model.session.CodexRuntimeModels.RuntimeConnectedRequest;
import com.agenttaskmanager.app.model.session.CodexRuntimeModels.RuntimeDisconnectedRequest;
import com.agenttaskmanager.app.model.session.CodexRuntimeModels.RuntimeEventPublishRequest;
import com.agenttaskmanager.app.model.session.CodexSessionEventModels.SessionEventEnvelope;
import java.util.List;
import java.util.function.Consumer;

public interface CodexSessionPlatformService {

  SessionSummary createSession(
      CreateSessionRequest request,
      String userId,
      String deviceId,
      String hostName
  );

  List<SessionSummary> listSessions(int limit, String userId);

  SessionDetail getSession(String sessionId, String userId);

  SessionDetail attachSession(String sessionId, AttachSessionRequest request, String userId);

  SessionDetail resumeSession(String sessionId, ResumeSessionRequest request, String userId);

  SessionDetail submitTurn(String sessionId, SubmitTurnRequest request, String userId);

  SessionDetail markRuntimeConnected(String sessionId, RuntimeConnectedRequest request, String userId);

  SessionDetail markRuntimeDisconnected(String sessionId, RuntimeDisconnectedRequest request, String userId);

  SessionDetail publishRuntimeEvent(String sessionId, RuntimeEventPublishRequest request, String userId);

  List<SessionEventEnvelope> listEvents(String sessionId, String afterEventId, int limit, String userId);

  SessionEventSubscription subscribeEvents(
      String sessionId,
      String afterEventId,
      int replayLimit,
      Consumer<SessionEventEnvelope> consumer,
      String userId
  );
}
