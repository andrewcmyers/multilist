# multilist
Multilist is a Java checklist/TODO application.  It runs using JavaFX currently
and is intended to run as an Android application. The goal is also to use
Fabric (http://www.cs.cornell.edu/projects/Fabric) as the back end for
persistent storage, so that checklists can be shared securely among multiple
users.

Multilist rolls together checklist items and lists as the same sort of thing,
so a todo item can have subitems. Unlike other checklist applications, there is
not a tree of items; multiple todo items can depend on the same todo item, as
long as the dependencies do not form a cycle.

See the file LICENSE for information about using this code.

Andrew Myers, July 2015
