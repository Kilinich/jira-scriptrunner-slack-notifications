// Slack notification script for Jira Scriptrunner plugin by Kilinich
// Usage: Paste in Script Listeners firing on Issue Updated event
//
def issueTransit = false
def issueReqApprov = false
def issueDone = false
def approversList = []
def watchersList = []
def doNotNotifyList = ['robot-manager']
//
// Method to post message via slack incoming webhook, url is stored in SLACK_WEBHOOK_URL in Scriptrunner global variables
//
def getReqType() {
    if (issue.fields.customfield_10016.hasProperty('requestType')) {
        issue.fields.customfield_10016.requestType.name
    } else {
        issue.fields.issuetype.name
    }
}
def postSlackMsg(toChannel, msg, attach ='') {
   logger.info("Posting message to $toChannel: '${msg.take(30)}...'")
   post(SLACK_WEBHOOK_URL)
            .header('Content-Type', 'application/json')
            .body([
                channel: toChannel,
                mrkdwn: 'true',
                text: msg,
                attachments: [[text: attach]]
            ])
           .asString()
}
//
// Detect if issue is just transitioned
//
def statusChange = changelog.items.find {it.field.equals('status')}
if (statusChange) {
     logger.info("Issue $issue.key transitioned from status [$statusChange.fromString] to [$statusChange.toString]")
     issueTransit = true
     if (statusChange.toString.equals('Done')) {issueDone = true}
}
// Detect if issue require approval and get approvers
// customfield_10020 ("Approvals" in our currnt instance) contain info about all issue's aprovals
if (issueTransit && issue.fields.customfield_10020) {

    def pendingApproval = issue.fields.customfield_10020.find {it.finalDecision.equals('pending')}
    if (pendingApproval) {
        logger.info("Pending approval: $pendingApproval.id")
    }

    get("rest/api/2/issue/${issue.key}")
    .header('Content-Type', 'application/json')
    .asObject(Map).body.fields.customfield_10020
    .each {
        approvalItem ->
        if (approvalItem.finalDecision.equals('pending')) {
            issueReqApprov = true
            logger.info("Approval ID $approvalItem.id decision: $approvalItem.finalDecision")
            approvalItem.approvers.each {
                approverItem -> approversList.add(approverItem.approver.name)
            }
            logger.info("Issue require approval of: $approversList")
        }
    }
}
//
// Notify approvers
//
if (issueReqApprov) {
    approversList.each { username ->
    postSlackMsg("@$username", ":nerd: *Your approval required*\n<$JIRA_SD_URL$issue.key|$issue.key: ${issue.fields.summary.take(80)}>\n${getReqType()} from <@$issue.fields.reporter.name> transitioned to `$issue.fields.status.name` status by <@$user.name> and now require your action to ${if (issueDone) 'close' else 'move on'}.", issue.fields.description)
    }
}
//
// Notify watchers (except approvers and initiator) and initiator if issue is done
//
if (issueDone) {
// Get issue watchers
    get("rest/api/2/issue/${issue.key}/watchers")
        .header('Content-Type', 'application/json')
        .asObject(Map).body.watchers.each {watcher -> watchersList.add(watcher.name)}
    logger.info("Watchers: $watchersList")
    (watchersList - doNotNotifyList - user.name - issue.fields.reporter.name).each {
        watcher ->
        postSlackMsg("@$watcher", ":coffee: *Work is done*\n<$JIRA_SD_URL$issue.key|$issue.key: ${issue.fields.summary.take(80)}>\n${getReqType()} from <@$issue.fields.reporter.name> resolved by <@$user.name>, you are watching it so take a look if you want.",issue.fields.description)
    }
}
