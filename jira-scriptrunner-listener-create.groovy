// Slack notification script for Jira Scriptrunner plugin by Kilinich
// Usage: Paste in Script Listeners firing on Issue Created event
// New issue notification config, keys is case-sensitive!
// Format: ['key':'channel', 'key':['channel1','channel2', ...], ...] 
def notifyByIssueType = ['Incident':'#DevOps', 'Change request':'@kilinich', 'Service request':['@kilinich','@aolkhovsky']]
def notifyByRequestType = ['Production incident':'#data-issues', 'Deploy request':'#deploy']
def notifyByComponent = ['Jira Service Desk':'@kilinich']
// Field IDs for our instance of Jira 
def reqTypeField = 'customfield_10016'
// Slack icons mapping by issue type
def icons = ['Incident':':incident:', 'Service request':':service:','Change request':':change:', 'Task':':task:','Sub-task':':task:']
def defaultIcon = ':incident:'
// Method to post message via slack incoming webhook, url is stored in SLACK_WEBHOOK_URL in Scriptrunner global variables
def postSlackMsg(toChannel, msg, attach ='') {
    def resp = post(SLACK_WEBHOOK_URL)
    .header('Content-Type', 'application/json')
    .body([
        channel: toChannel,
        mrkdwn: 'true',
        text: msg,
        attachments: [[text: attach]]
    ])
    .asString().statusText
    logger.info("Slack message to [$toChannel] '${msg.take(50)}...' $resp")       
}
def issueType = issue.fields.issuetype.name
def reqType = issue.fields."$reqTypeField" ? issue.fields."$reqTypeField".requestType.name : issueType
def components = issue.fields.components.collect{it.name}
logger.info("Issue [$issue.key] created, type: [$issueType] request type: [$reqType] components: $components")
// Build recipients list and notify
def recipients = []
recipients.add(notifyByIssueType[issueType])
recipients.add(notifyByRequestType[reqType])
components.each{recipients.add(notifyByComponent[it])}
recipients = recipients.flatten().unique() - null
logger.info("Notifying recipients: $recipients") 
recipients.each {
    postSlackMsg(it,"${icons[issueType] ?: defaultIcon} *New ${issueType.toLowerCase()}*\n<$JIRA_SD_URL$issue.key|$issue.key: ${issue.fields.summary.take(80)}>\n$reqType from <@$issue.fields.reporter.name> with `$issue.fields.priority.name` priority require initial analisys.", issue.fields.description)
}