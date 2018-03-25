// Slack notification script for Jira Scriptrunner plugin by Kilinich
// Usage: Paste in Script Listeners firing on issue commented event
def reqTypeField = 'customfield_10016'
def doNotNotifyList = ['robot-manager']
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
// Get request issue type and request type if exists, components 
def issueType = issue.fields.issuetype.name
def reqType = issue.fields."$reqTypeField" ? issue.fields."$reqTypeField".requestType.name : issueType
logger.info("Issue [$issue.key], type: [$issueType] request type: [$reqType], commented by $comment.author.name")
// Build watchers list and notify
def watchersList = get("rest/api/2/issue/$issue.key/watchers")
.header('Content-Type', 'application/json')
.asObject(Map).body.watchers.collect{it.name}
logger.info("Watchers: $watchersList, assignee: $issue.fields.assignee.name" )
(watchersList + issue.fields.assignee.name - comment.author.name - doNotNotifyList).flatten().unique().each { username ->
    postSlackMsg("@$username", ":incoming_envelope: *$issueType commented by* <@$comment.author.name>\n<$JIRA_SD_URL$issue.key|$issue.key: ${issue.fields.summary.take(80)}>", comment.body)
}