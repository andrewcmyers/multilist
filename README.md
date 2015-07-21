# multilist
Multilist is a Java checklist/TODO application.  It rolls together checklist
items and lists as the same sort of thing, so a todo item can have subitems.
Unlike other checklist applications, there is not a tree of items; multiple
todo items can depend on the same todo item, as long as the dependencies do not
form a cycle.

Multilist currently runs using JavaFX and saves app state to a local file.  The
longer term goal is to make it into an Android application and to use use
Fabric (http://www.cs.cornell.edu/projects/Fabric) as the back end for
persistent storage. Using Fabric will allow checklists to
be shared securely among multiple users.  See the file LICENSE for
information about using this code.

Andrew Myers, July 2015
