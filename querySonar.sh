echo "current branch is : "$BUILDKITE_BRANCH
if [ $BUILDKITE_PULL_REQUEST = "false" ]; then
	SonarStatus_Url="https://sonarcloud.io/api/qualitygates/project_status?projectKey=java-xbo&branch="$BUILDKITE_BRANCH
	Status=`curl -s $SonarStatus_Url | jq '.projectStatus.status'`
	echo "current branch sonarcloud status is : "$Status
	if [ x"$Status" = x'"ERROR"' ]; then
    	echo "Sonar Check Failed"
    	echo "Please visit https://sonarcloud.io/dashboard?branch="$BUILDKITE_BRANCH"&id=java-xbo for more details"
    	exit 1
	else
    		echo "Sonar Check Pass"
    		exit 0
	fi
else
	echo "current PR number is : "$BUILDKITE_PULL_REQUEST
	SonarStatus_Url="https://sonarcloud.io/api/qualitygates/project_status?projectKey=java-xbo&pullRequest="$BUILDKITE_PULL_REQUEST
	Status=`curl -s $SonarStatus_Url | jq '.projectStatus.status'`
	echo "current branch sonarcloud status is : "$Status
	if [ x"$Status" = x'"ERROR"' ]; then
    		echo "Sonar Check Failed"
    		echo "Please visit https://sonarcloud.io/dashboard?id=java-xbo&pullRequest="$BUILDKITE_PULL_REQUEST" for more details"
    		exit 1
	else
    		echo "Sonar Check Pass"
    		exit 0
	fi
fi