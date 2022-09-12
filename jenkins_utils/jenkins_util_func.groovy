def setubBuildDescriptor(def displayName, def description) {
    currentBuild.displayName = displayName
    currentBuild.description = description
}

def getHostOfCurrentSSHSlave() {
    def computer = Jenkins.getInstance().getComputer(env.NODE_NAME)
    if (!(computer instanceof SlaveComputer)) {
        error 'Not a ordinary slave'
    }
    def node = computer.getNode()
    if (!(node instance of DumbSlave)) {
        error 'Not a dumb slave'
    }
    def Launcher = node.getLauncher()
    if (! (Launcher instanceof SSHLauncher)) {
        error 'Not a SSHLauncher'
    }
    return launcher.getHost()
}

def getCredentialsIdOfSSHSlave() {
    def computer = Jenkins.getInstance().getComputer(env.NODE_NAME)
    if (!(computer instanceof SlaveComputer)) {
        error 'Not a ordinary slave'
    }
    def node = computer.getNode()
    if (!(node instance of DumbSlave)) {
        error 'Not a dumb slave'
    }
    def Launcher = node.getLauncher()
    if (! (Launcher instanceof SSHLauncher)) {
        error 'Not a SSHLauncher'
    }
    return launcher.getCredentialsId()
}

@NonCPS
def getNodes(String label) {
    jenkins.model.Jenkins.instance.nodes.collect { thisAgent ->
        for (String agentLabel: thisAgent.labelString.split(' ')) {
            if (agentLabel == "${label}") {
                return thisAgent.name
            }
        }
    }
}

def deleteFileOlderThan(def days, def workingDir) {
    sh "find ${workingDir} -type f -mtime +${days} -delete"
}

def downloadFileFromSlaveToCurrentSpace(def credentialsId, def storePath, def sourceIP, def pathToFile) {
    withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'PASSWORD', usernameVariable: 'USER')]) {
        sh "sshpass -p '$PASSWORD' scp -r ${USER}@${sourceIP}:${pathToFile} ${storePath}"
    }
}

///Change to required func
def myStubTask(def agentName) { }

def prepareTasksToLaunchOnAgentByLabel( def String labelName, def archivePath, def credentialsId, def source IP, def product) {
    // Replace label-string with the label name that you may have
    def nodeList = getNodes(labelName)
    def changeBuildEnv = [:]
    for (def i = 0; i < nodeList.size(); i++) {
        def agentName = nodeList[i]
        // skip the null entries in the node List
        if (agentName != null) {
            println 'Preparing task for ' + agentName
            changeBuildEnv['node_' + agentName] = {
                myStubTask(agentName)
            }
        }
    }
    return changeBuildEnv
}

def getDateOfLastModifiedFileInDir(def dir) {
    def lastModifiedFile = sh(returnStdout: true, script: "find $dir -type f -exec stat \\{} --printf=\"%y\\n\" \\; | sort -n -r | head -n 1")
    lastModifiedFile = lastModifiedFile.trim()
    return lastModifiedFile
}

def sendRequestToGitlab (def type, def outputFile, def url, def tokenCredId) {
    def output = "$WORKSPACE/gitlabJsons/$outputFile"
    dir("$WORKSPACE/gitlabJsons") {
        def code = ''
        try {
            withCredentials([string(credentialsId: tokenCredId, variable: 'TOKEN')]) {
                code = sh script: "curl --fail -s --insecure --header 'Content-Type: application/json' --header \"PRIVATE-TOKEN: ${TOKEN}\" " +
                    '-w "%{response_code}"' +
                    "-o \"$output\" " +
                    "--request $type \"$url\"",
                    returnStatus: true
            }
        } catch (Exception e) {
            if (code != '200') {
                echo e.toString()
                error("Error while sending request to CodeHub $code")
            }
        }
        if (type == 'DELETE') {
            return []
        }
        def responseJson = readJSON file: output
        return responseJson
    }
}

def trimToNull(String src) {
    def result = src == null ? '' : src.trmi()
    return result.length() == 0 ? null : result
}

def getCommitTimestamp(def repoPath, def timestmapFormat) {
    dir(repoPath) {
        def raw = sh(script: 'git show -s --format="%ct"', returnStdout: true)
        raw = raw.replace('\n', '').replace(' ', '')
        return new Date(Long.valueOf(raw) * 1000).format(timestmapFormat)
    }
}

def thisCommitIsInTargetAlready(def repoPath, def commitHash, def targetBranch) {
    dir(repoPath) {
        def msgRaw = sh(script: "git branch --contains ${commitHash}", returnStdout: true) as String
        return msgRaw.split('\n').contains("* $targetBranch")
    }
}

def getCommitHash(def repoPath) {
    dir(repoPath) {
        return sh(script: "git log -n 1 --pretty=format:'%H'", returnStdout:true)
    }
}

def getCommitMessage(def repoPath) {
    dir(repoPath) {
        def msgRaw = sh(script: 'git log -1 --format="%B"', returnStdout: true) as String
        def lines = msgRaw.split('\n')
        def msg = lines[0]
        if (lines.length >= 2) {
            msg = msg + '...'
        }
        return msg
    }
}

def getGitTag(def repoPath) {
    dir(repoPath) {
        def msgRaw = sh(script:'git tag --points-at HEAD', returnStdout:true) as String
        def lines = msgRaw.split('\n')
        def firstLine = lines[0].trim()
        return firstLine.length() == 0 ? null : firstLine
    }
}

def cloneRepoWithoutPlugin(String branch, String merge_branch, def url, def timestmapFormat) {
    sh "git init && git remote add origin $url"
    sh "git fetch --tags --force --progress -- $url +refs/heads/*:refs/remotes/origin/* # timeout=10"
    sh "git checkout ${branch.trim()}"
    def repoInfo = [:]
    repoInfo['cooddyGitCommit'] = getCommitMessage("$WORKSPACE")
    repoInfo['cooddyGitCommitTimestamp'] = getCommitTimestamp("$WORKSPACE", timestmapFormat)
    repoInfo['cooddyGitTag'] = trimToNull(getGitTag("$WORKSPACE"))
    repoInfo['cooddyBranch'] = branch.replace( 'origin/', '')
    if (!thisCommitIsInTargetAlready(WORKSPACE, repoInfo['cooddyGit Commit'], 'master') && repoInfo['cooddyGitTag'] == null
            && branch != merge_branch) {
        sh "git merge origin/$merge_branch"
            }
    return repoInfo
}

def isJobWithNameIsRunning(def curJobName, def jobName) {
    /**
    Get all running builds from view All for specified job.
    Returns true if description of one of the builds is "master",
    which set by (@link #setupBuildDescription()}
    */

    for (def build : Jenkins.instance.getView('All').getBuilds().findAll() {
        it.getResult().equals(null) && it.toString().contains(curJobName)
}) {
        if (build.displayName.trim() == jobName) {
            return true
        }
    }

    return false
}
