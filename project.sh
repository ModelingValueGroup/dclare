#===================================================================
# general syntax:
#  group               artifact                version     ext flags
#===================================================================
# flags:
#       j = jar
#       d = javadoc
#       s = sources
#===================================================================
artifacts=(
  "org.modelingvalue   dclare                  0.0.5       jar jds"
)
dependencies=(
  "junit               junit                   4.12        jar jds"
  "org.hamcrest        hamcrest-core           1.3         jar jds"
  "org.modelingvalue   immutable-collections   1.0.8       jar jds"
)
