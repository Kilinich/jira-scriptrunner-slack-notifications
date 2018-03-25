// Slack notification script for Jira Scriptrunner plugin by Kilinich
// Usage: Paste in Script Listeners firing on Issue Updated event
// Notifying approvers and watchers
//
def doNotNotifyList = ['robot-manager']
// Field IDs for our instance of Jira 
def reqTypeField = 'customfield_10016'
def approvalsField = 'customfield_10020'
//
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
// Detect if issue is just transitioned
if (changelog.items.find{it.field.equals('status')}) {
    def issueType = issue.fields.issuetype.name
    def reqType = issue.fields."$reqTypeField" ? issue.fields."$reqTypeField".requestType.name : issueType
    def issueDone = changelog.items.find{it.field.equals('status')}.toString.equals('Done')
    logger.info("Issue [$issueType] request [$reqType] is done: $issueDone")
    // Detect if issue require approval and get approvers
    def approversList = []
    if (issue.fields."$approvalsField") {
        // this is not used due to bug in Jira / Scriprunner - sometimes it's just null
        def pendingApproval = issue.fields."$approvalsField".find{it.finalDecision.equals('pending')}
        if (pendingApproval) {logger.info("Pending approval (static debug): $pendingApproval.id")}
        // get from REST API instead of static objects
        pendingApproval = get("rest/api/2/issue/$issue.key")
        .header('Content-Type', 'application/json')
        .asObject(Map).body.fields."$approvalsField".find{it.finalDecision.equals('pending')}
        if (pendingApproval) {
            logger.info("Pending approval: $pendingApproval.id")
            approversList = pendingApproval.approvers.collect{it.approver.name}
            logger.info("Issue require approval of: $approversList")
        }
    }
    // Notify approvers if exists
    approversList.each { username ->
    postSlackMsg("@$username", ":nerd: *Your approval required*\n<$JIRA_SD_URL$issue.key|$issue.key: ${issue.fields.summary.take(80)}>\n$reqType from <@$issue.fields.reporter.name> transitioned to `$issue.fields.status.name` status by <@$user.name> and now require your action to ${if (issueDone) 'close' else 'move on'}.", issue.fields.description)
    }
    // Notify watchers (except approvers and event initiator) if issue is done
    if (issueDone) {
        def watchersList = get("rest/api/2/issue/$issue.key/watchers")
        .header('Content-Type', 'application/json')
        .asObject(Map).body.watchers.collect{it.name}
        logger.info("Watchers: $watchersList")
        (watchersList - doNotNotifyList - user.name - approversList).each {
            watcher ->
            postSlackMsg("@$watcher", ":coffee: *Work is done*\n<$JIRA_SD_URL$issue.key|$issue.key: ${issue.fields.summary.take(80)}>\n$reqType from <@$issue.fields.reporter.name> resolved by <@$user.name>, you are watching it so take a look if you want.", issue.fields.description)
        }
    }
}
