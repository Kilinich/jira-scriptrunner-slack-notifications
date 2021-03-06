// Slack notification script for Jira Scriptrunner plugin by Kilinich
// Usage: Paste in Script Listeners firing on issue commented event
def mentionPatterns = ['\\B@([a-z]+)\\s','\\[\\~([a-z]+)\\]']
def reqTypeField = 'customfield_10016'
def doNotNotifyList = ['robot-manager']
def ignoreAuthors = ['robot-manager']
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
logger.info("Issue [$issue.key], type: [$issueType] request type: [$reqType], commented by $comment.author.name")
if (!ignoreAuthors.contains(comment.author.name)) {
    // Build watchers list and notify
    def watchersList = get("rest/api/2/issue/$issue.key/watchers")
    .header('Content-Type', 'application/json')
    .asObject(Map).body.watchers.collect{it.name}
    if (issue.fields.assignee) {watchersList.add(issue.fields.assignee.name)}
    logger.info("Watchers and assignee: $watchersList")    
    (watchersList - comment.author.name - doNotNotifyList).flatten().unique().each { username ->
        postSlackMsg("@$username", ":incoming_envelope: *$issueType commented by* <@$comment.author.name>\n<$JIRA_SD_URL$issue.key|$issue.key: ${issue.fields.summary.take(80)}>", comment.body)
    }
    // Look for mentions and notify
    def mentionList = []
    mentionPatterns.each{ regex -> 
        (comment.body =~ /${regex}/).each{mentionList.add(it[1])}
    }
    logger.info("Mentions in comment: $mentionList")
    (mentionList - doNotNotifyList).flatten().unique().each { username ->
        postSlackMsg("@$username", ":mega: *You mentioned by* <@$comment.author.name> *in comment of ${issueType.toLowerCase()}*\n<$JIRA_SD_URL$issue.key|$issue.key: ${issue.fields.summary.take(80)}>", comment.body)
    }
}