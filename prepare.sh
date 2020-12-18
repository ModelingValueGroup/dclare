#!/usr/bin/env bash
##~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
## (C) Copyright 2018-2020 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
##                                                                                                                     ~
## Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in      ~
## compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0  ~
## Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on ~
## an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the  ~
## specific language governing permissions and limitations under the License.                                          ~
##                                                                                                                     ~
## Maintainers:                                                                                                        ~
##     Wim Bast, Tom Brus, Ronald Krijgsheld                                                                           ~
## Contributors:                                                                                                       ~
##     Arjan Kok, Carel Bast                                                                                           ~
##~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

set -ue

##########################################################################
# run this script to set things up
# the lib folder will be filled from the project.sh file
##########################################################################

. ~/secrets.sh # for $GITHUB_TOKEN

echo "## clearing out lib folder..."
rm -f lib/*.jar lib/*.pom

echo "## downloading buildtools..."
curl \
        --location \
        --remote-header-name \
        --remote-name \
        --fail \
        --silent \
        --show-error \
        "https://github.com/ModelingValueGroup/buildtools/releases/latest/download/buildtools.jar"
mv buildtools.jar ~/buildtools.jar

echo "## generate pom from project.sh..."
. <(java -jar ~/buildtools.jar)
generateAll

echo "## get dependencies from maven..."
(
    mvn dependency:copy-dependencies -Dmdep.stripVersion=true -DoutputDirectory=lib || :
    mvn dependency:copy-dependencies -Dmdep.stripVersion=true -DoutputDirectory=lib -Dclassifier=javadoc || :
    mvn dependency:copy-dependencies -Dmdep.stripVersion=true -DoutputDirectory=lib -Dclassifier=sources || :
) > /tmp/prepare.log
(   getAllDependencies "$GITHUB_TOKEN" 2>&1 \
        | fgrep -v --line-buffered 'could not download artifact: org.modelingvalue:' \
        | fgrep -v --line-buffered 'missing dependency org.modelingvalue:' \
        | fgrep -v --line-buffered '::info::no snapshot for '
) || :

echo "## lib folder contents:"
(   cd lib
    rm -f *.pom
    ls | sed 's/-javadoc//;s/-sources//;s/^/    INFO: /' | sort -u 1>&2
)
