/// Uses https://github.com/jenkinsci/workflow-remote-loader-plugin to launch any script file

def cooddyUrl=params.repoUrl
def cloneRepo (def branch, def merge_branch, def url, def credId) {
    checkout (
        [$class: 'GitSCM',
        branches: [[name: branch]],
        doGenerateSubmoduleConfigurations: false,
        extensions : [[$class: 'SubmoduleOption',
                        disableSubmodules : true,
                        recursiveSubmodules: true,
                        trackingSubmodules : false],
                    [$class 'PreBuildMerge',
                        options:[fastForwardMode: 'FF',
                                mergeRemote : 'origin',
                                mergeStrategy: 'DEFAULT',
                                mergeTarget : merge_branch]
                    ]],
        submoduleCfg: [],
        userRemoteConfigs : [[credentialsId: credId,
                            url : url]]]
    )
}
node('build') {
    String scriptBranchenv.gitlabSourceBranch != null ? env.gitlabSourceBranch: 'master'
    cloneRepo (scriptBranch, 'master', cooddyUrl, params.credId)
    //Example: fileLoader.load('ci/utils/main_pipeline')
    fileLoader.load(params.mainPipeline)
}
