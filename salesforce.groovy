#!groovy
pipeline{
	agent { 
    		kubernetes { 
      		label 'salesforce-deploy' 
      		defaultContainer 'jnlp' 
      		yaml """ 
			apiVersion: v1 
			kind: Pod 
			metadata: 
  			labels: 
    				some-label: salesforce-deploy 
			spec: 
  			containers: 
  				- name: salesforce-deploy 
    					image: ${ARN_ID}.dkr.ecr.us-east-1.amazonaws.com/salesforce-deploy:latest 
    			securityContext: 
      			privileged: true 
    			resources: 
      			limits: 
        			memory: "2Gi" 
        			cpu: "2" 
      			requests: 
        			memory: "1Gi" 
        			cpu: "1" 
    			tty: true 
    			command: 
    				- cat 
    			env: 
    				- name: AWS_DEFAULT_REGION 
      			value: us-east-1´ 
			""" 
    } 
  } 	
    stages{
        stage('configs'){
            steps{
                script{
                dir ("configs"){   
                sh 'pwd && echo ${JOB_NAME} && echo ${WORKSPACE}'
                load "${JOB_NAME}.groovy"
                }
                dir('Groovy') {
			checkout([$class: 'GitSCM', branches: [[name: 'master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CleanBeforeCheckout']], gitTool: '${gitInit}', submoduleCfg: [], userRemoteConfigs: [[credentialsId: "${env.userIdGit}", url: "git@${org}:${squad}/libs.git"]]])
					lib = load "jira.groovy"
		        }
                }//script
                }//steps
        }//load  
        stage ('initialize') {
            steps{
                script{
                sh 'chmod 777 ${WORKSPACE}/parametros/init.sh && ${WORKSPACE}/parametros/init.sh'
                }//script
            }//steps
        }//initialize
        stage ('clone'){
            steps{
                script{
                    dir ("${ProjectDir}"){
            checkout([$class: 'GitSCM', branches: [[name: "${env.branchName}"]], doGenerateSubmoduleConfigurations: false, gitTool: '${gitTool}', submoduleCfg: [], userRemoteConfigs: [[credentialsId: "${env.userIdGit}", url: "${env.UrlGit}"]]])
                    }
                }//script
            }//steps
        }//clone
        stage ('clean'){
                steps{
                    script{
                        dir ("${ProjectDir}"){
                configFileProvider([configFile(fileId: "${env.managedFiles}", variable: 'GRADLE_PROPERTIES')]) { 
	            sh "mv ${GRADLE_PROPERTIES} gradle.properties && ${grdHm}/gradle clean --stacktrace -DskipTests"
                }
                }
                }//script
                }//steps
        }//clean
        stage ('build'){
            steps{
                script{
                    dir ("${ProjectDir}"){
                configFileProvider([configFile(fileId: "${env.managedFiles}", variable: 'GRADLE_PROPERTIES')]) { 
	            sh "mv ${GRADLE_PROPERTIES} gradle.properties && ${grdHm}/gradle build -D skipTests --full-stacktrace"
	                              }//provide
                    }
                sh 'chmod 777 ${WORKSPACE}/parametros/buildpack.sh && ${WORKSPACE}/parametros/buildpack.sh'
                }//script
            }//steps
        }//build
        stage ('Test Org'){
                steps{
                    script{
                        dir ("${ProjectDir}"){
                configFileProvider([configFile(fileId: "${env.managedFiles}", variable: 'GRADLE_PROPERTIES')]) { 
	            sh "mv ${GRADLE_PROPERTIES} gradle.properties && ${grdHm}/gradle test --stacktrace"
                }
                }
                }//script
            }//steps
        }//test
        stage ('package') {
         steps{
            script{
            sh 'chmod 777 ${WORKSPACE}/parametros/rpmcreate.sh && ${WORKSPACE}/parametros/rpmcreate.sh'
           }//script
       }//steps
      }//package
        stage ('publish'){
            steps{
                script{
                dir ("${rpmUpload}"){
                def server = Artifactory.newServer url: "${UrlRepoApp}", credentialsId: "${userIdRepoApp}"
                def uploadSpec = "{\"files\": [{\"pattern\": \"${pkgName}-${pkgVersion}-${pkgArch}\", \"target\": \"${repositoryName}${repositoryDir}\"}]}"
                server.upload(uploadSpec)
                }//dir
                   }//script
               }//steps
        }//publish        
       stage('deploy'){
            steps{
              script{
                   withCredentials([usernamePassword(credentialsId: "${userId}", usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                   sh "antDeploy.sh ${USERNAME} "
                   }
                   try{
                        sh "antDeploy.sh ${pkgName}"                        
                   }catch(Exc){
                       println ("Erro ao aplicar o pacote Org Salesforce")
                   }
               }//script
            }//steps
        }//deploy
        stage('Jira'){
            steps{
                script{
                    def projetoJira = "${env.gitlabSourceNamespace}"
                    if (env.projectJira) { projetoJira = "${env.projectJira}" }
                    def cardKey = libJira.JiraCardCI("${userPuppetId}", "${projetoJira}", "${env.pkgName}.${env.pkgVersion}.rpm", "${pkgVersion}", "${gitlabMergeRequestLastCommit}", "${gitlabUserName}", "${gitlabBranch}", "${gitlabSourceRepoName}", "${env.ArtiHash}", "${gitlabSourceNamespace}", "${versionBranch}", "${BUILD_ID}", "prod")                        
                    println "Card Criado: ${cardKey}"
                }//script
            }//steps
        }//stage Jira        
    }//stages
}//pipeline
