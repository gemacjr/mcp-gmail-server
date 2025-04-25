Gmail MCP Server
A Java-based Model Context Protocol (MCP) server that integrates with Gmail to enable natural language email queries via Claude Desktop. The server exposes tools to search emails by topic (e.g., "project deadlines") and read email content, using the Gmail API with secure OAuth2 authentication.
Features

Search Emails: Find emails by topic, sender, or date using Gmail's query syntax (e.g., from:alice budget).
Read Emails: Retrieve full email content (subject, sender, body) by ID.
Secure Authentication: Uses OAuth2 with gmail.readonly scope for minimal access.
MCP Integration: Communicates with Claude Desktop via Stdio using JSON-RPC 2.0.
Robust Design: Includes error handling, logging, and input validation for production readiness.

Prerequisites

Java 17: Ensure JDK 17 is installed (java --version).
Maven: For dependency management (mvn --version).
Google Cloud Project: With Gmail API enabled and OAuth2 credentials.
Claude Desktop: Installed and configured for MCP (available from Anthropic).
Operating System: Tested on Windows, macOS, and Linux.

Setup Instructions
1. Create Google Cloud Credentials

Go to the Google Cloud Console.
Create a new project (e.g., "Gmail MCP Server").
Enable the Gmail API:
Navigate to APIs & Services > Library.
Search for "Gmail API" and click Enable.


Create OAuth 2.0 credentials:
Go to APIs & Services > Credentials > Create Credentials > OAuth 2.0 Client IDs.
Select Desktop app as the application type.
Name the client (e.g., "Gmail MCP Client").
Download the credentials.json file.


Add http://localhost to authorized redirect URIs:
In Credentials, edit the OAuth 2.0 Client ID and add http://localhost under Authorized redirect URIs.



2. Clone or Set Up the Project

Clone the repository (or create a new Maven project):git clone <repository-url>
cd gmail-mcp-server

Alternatively, create a new Maven project and copy the provided files:
src/main/java/com/example/gmailmcp/GmailMcpServer.java
pom.xml
src/main/resources/credentials.json (from Google Cloud)
src/main/resources/logback.xml


Place credentials.json in src/main/resources/.
Verify the project structure:gmail-mcp-server/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/gmailmcp/
│   │   │       └── GmailMcpServer.java
│   │   ├── resources/
│   │   │   ├── credentials.json
│   │   │   └── logback.xml



3. Build the Project

Install dependencies and build the project:mvn clean install


The build will generate a shaded JAR: target/gmail-mcp-server-1.0-SNAPSHOT.jar.

4. Run the Server

Start the server:java -jar target/gmail-mcp-server-1.0-SNAPSHOT.jar


On first run, the server opens a browser for OAuth2 authentication:
Log in with your Gmail account.
Grant permission for the app to read emails (uses gmail.readonly scope).
Tokens are saved to ~/.gmail-mcp/tokens for subsequent runs.


The server runs in Stdio mode, waiting for MCP requests from Claude Desktop.

5. Configure Claude Desktop

Install Claude Desktop from Anthropic’s website.
Locate or create the MCP configuration file (e.g., ~/.cursor/mcp.json or Claude’s settings file).
Add the Gmail MCP server configuration:{
"mcpServers": {
"gmail": {
"command": "java",
"args": ["-jar", "/full/path/to/gmail-mcp-server-1.0-SNAPSHOT.jar"]
}
}
}

Replace /full/path/to/gmail-mcp-server-1.0-SNAPSHOT.jar with the absolute path to your JAR file (e.g., /home/user/gmail-mcp-server/target/gmail-mcp-server-1.0-SNAPSHOT.jar).
Save the file and restart Claude Desktop.

6. Test the Integration

Open Claude Desktop.
Enter a natural language query, such as:Find emails about project deadlines from the last 30 days.


Claude translates this to a searchEmails tool call (e.g., query: "project deadlines after:2025-03-26"). The server returns a list of emails:[
{"id": "abc123", "subject": "Project Deadlines Update", "snippet": "Please review the June 1 deadline..."},
{"id": "def456", "subject": "Q2 Deadlines", "snippet": "Deadlines for Project X..."}
]


Follow up with:Read the email with ID abc123.


The server executes readEmail("abc123") and returns:{
"id": "abc123",
"subject": "Project Deadlines Update",
"from": "jane.doe@example.com",
"body": "Hi team, please review the June 1 deadline for Project X..."
}


Claude summarizes the results in natural language.

Usage Examples

Search by Topic:Show me emails about budget approvals from last month.

Maps to: searchEmails("budget approvals after:2025-03-01 before:2025-04-01").
Search by Sender:Find emails from alice@example.com about conference planning.

Maps to: searchEmails("from:alice@example.com conference planning").
Read Specific Email:Read the email with ID xyz123.

Maps to: readEmail("xyz123").

Best Practices

Security:
Uses minimal gmail.readonly scope to limit access.
Stores OAuth2 tokens securely in ~/.gmail-mcp/tokens (excluded from version control).
Validates inputs to prevent injection attacks.


Error Handling:
Returns JSON-RPC error responses for invalid requests or API failures.
Logs errors with SLF4J/Logback for debugging.


Performance:
Limits search results to 10 emails to respect Gmail API rate limits (250 queries/minute).
Uses efficient JSON parsing with Jackson.


Maintainability:
Modular code with separated Gmail and MCP logic.
Comprehensive logging for monitoring.



Troubleshooting

Authentication Errors:
Ensure credentials.json is in src/main/resources/.
Delete ~/.gmail-mcp/tokens and re-authenticate if tokens are invalid.


Server Not Responding:
Verify the JAR path in mcp.json is correct.
Check logs in the console for errors.


Claude Desktop Issues:
Ensure Claude Desktop is updated to the latest version.
Confirm the MCP configuration file is correctly formatted.


Gmail API Rate Limits:
If you hit rate limits, reduce query frequency or implement exponential backoff.



Security Considerations

Credentials: Add credentials.json and ~/.gmail-mcp/tokens to .gitignore to prevent accidental commits.
OAuth2: Uses offline access type for persistent tokens; review Google Cloud Console for unusual activity.
Scope: Limited to gmail.readonly to prevent unauthorized actions.

Extending the Server

Add Tools: Extend GmailMcpServer.java to include tools like labelEmail or sendEmail (requires additional scopes).
Remote Hosting: Modify to use SSE or WebSockets for cloud deployment (e.g., with Spring WebFlux).
Caching: Implement caching for frequently searched queries using an in-memory store like Caffeine.

Contributing
Contributions are welcome! Please:

Fork the repository.
Create a feature branch (git checkout -b feature/xyz).
Commit changes (git commit -m "Add XYZ feature").
Push to the branch (git push origin feature/xyz).
Open a pull request.

License
MIT License. See LICENSE for details.
Acknowledgments

Built with Google Gmail API and Anthropic MCP.
Inspired by community MCP servers like GongRzhe’s Gmail MCP Server.

For issues or questions, open a GitHub issue or contact the maintainer.
