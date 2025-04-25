import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

// Main class for Gmail MCP Server
public class GmailMcpServer {
    private static final Logger logger = LoggerFactory.getLogger(GmailMcpServer.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String APPLICATION_NAME = "Gmail MCP Server";
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);
    private static final String CREDENTIALS_FILE = "credentials.json";
    private static final String TOKENS_DIR = System.getProperty("user.home") + "/.gmail-mcp/tokens";
    private static Gmail gmailService;

    public static void main(String[] args) throws Exception {
        logger.info("Starting Gmail MCP Server...");
        initializeGmailService();
        runMcpServer();
    }

    // Initialize Gmail API client with OAuth2
    private static void initializeGmailService() throws Exception {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        // Load client secrets
        InputStream in = GmailMcpServer.class.getResourceAsStream("/" + CREDENTIALS_FILE);
        if (in == null) {
            throw new FileNotFoundException("Credentials file not found: " + CREDENTIALS_FILE);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(in));

        // Set up OAuth2 flow
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIR)))
                .setAccessType("offline")
                .build();

        // Authorize (manually for first run)
        Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
        logger.info("OAuth2 credentials initialized.");

        // Initialize Gmail service
        gmailService = new Gmail.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    // Run MCP server over Stdio
    private static void runMcpServer() throws IOException {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter stdout = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);

        // Send MCP handshake
        Map<String, Object> handshake = new HashMap<>();
        handshake.put("jsonrpc", "2.0");
        handshake.put("method", "handshake");
        handshake.put("params", Map.of(
                "capabilities", Map.of(
                        "tools", List.of(
                                Map.of("name", "searchEmails", "description", "Search Gmail for emails by topic",
                                        "parameters", Map.of("query", "string")),
                                Map.of("name", "readEmail", "description", "Read the content of a specific email by ID",
                                        "parameters", Map.of("emailId", "string"))
                        )
                )
        ));
        stdout.println(mapper.writeValueAsString(handshake));
        stdout.flush();
        logger.info("Sent MCP handshake.");

        // Process incoming JSON-RPC requests
        String line;
        while ((line = stdin.readLine()) != null) {
            try {
                Map<String, Object> request = mapper.readValue(line, Map.class);
                String method = (String) request.get("method");
                Map<String, Object> params = (Map<String, Object>) request.get("params");
                String id = String.valueOf(request.get("id"));

                Map<String, Object> response = new HashMap<>();
                response.put("jsonrpc", "2.0");
                response.put("id", id);

                try {
                    Object result = switch (method) {
                        case "searchEmails" -> searchEmails((String) params.get("query"));
                        case "readEmail" -> readEmail((String) params.get("emailId"));
                        default -> throw new IllegalArgumentException("Unknown method: " + method);
                    };
                    response.put("result", result);
                } catch (Exception e) {
                    logger.error("Error processing method {}: {}", method, e.getMessage());
                    response.put("error", Map.of(
                            "code", -32603,
                            "message", e.getMessage()
                    ));
                }

                stdout.println(mapper.writeValueAsString(response));
                stdout.flush();
            } catch (Exception e) {
                logger.error("Error processing request: {}", e.getMessage());
            }
        }
    }

    // Tool: Search emails by topic
    private static List<Map<String, String>> searchEmails(String query) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }
        logger.info("Searching emails with query: {}", query);

        Gmail.Users.Messages.List request = gmailService.users().messages().list("me").setQ(query).setMaxResults(10L);
        List<Message> messages = request.execute().getMessages();
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        return messages.stream().map(msg -> {
            String subject = "";
            try {
                // Fetch message metadata to get headers
                Message metadata = gmailService.users().messages().get("me", msg.getId()).setFormat("metadata").execute();
                subject = getHeader(metadata, "Subject");
            } catch (IOException e) {
                logger.warn("Failed to fetch subject for message {}: {}", msg.getId(), e.getMessage());
            }
            return Map.of(
                    "id", msg.getId(),
                    "snippet", msg.getSnippet(),
                    "subject", subject
            );
        }).collect(Collectors.toList());
    }

    // Tool: Read email by ID
    private static Map<String, String> readEmail(String emailId) throws IOException {
        if (emailId == null || emailId.trim().isEmpty()) {
            throw new IllegalArgumentException("Email ID cannot be empty");
        }
        logger.info("Reading email with ID: {}", emailId);

        Message message = gmailService.users().messages().get("me", emailId).setFormat("full").execute();
        String body = getEmailBody(message);
        return Map.of(
                "id", message.getId(),
                "subject", getHeader(message, "Subject"),
                "from", getHeader(message, "From"),
                "body", body
        );
    }

    // Helper: Get email header
    private static String getHeader(Message message, String name) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null) {
            return "";
        }
        return message.getPayload().getHeaders().stream()
                .filter(header -> name.equals(header.getName()))
                .map(MessagePartHeader::getValue)
                .findFirst()
                .orElse("");
    }

    // Helper: Get email body (text/plain or text/html)
    private static String getEmailBody(Message message) {
        MessagePart payload = message.getPayload();
        if (payload == null) {
            return "";
        }
        if (payload.getParts() == null && payload.getBody() != null && payload.getBody().getData() != null) {
            return new String(Base64.getUrlDecoder().decode(payload.getBody().getData()), StandardCharsets.UTF_8);
        }
        return payload.getParts().stream()
                .filter(part -> "text/plain".equals(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null)
                .map(part -> new String(Base64.getUrlDecoder().decode(part.getBody().getData()), StandardCharsets.UTF_8))
                .findFirst()
                .orElse("");
    }
}