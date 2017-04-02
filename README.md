# multilist
Multilist is a checklist/todo application for Android. It handles both one-time
todo items and recurring checklists. Multilist is designed to support multiple
reusable checklists, such as shopping lists, recipes, and trip checklists.
Importantly, these checklists are allowed to overlap. Multilist rolls together
checklist items and lists as the same sort of thing, so any checklist item can
have subitems.  However, unlike other checklist applications, it does not
impose a tree structure on items; instead, multiple items can depend on the
same item, as long as dependencies do not form a cycle. This is
handy when the same item can appear in multiple checklists.

Available on the Google Play Store at https://play.google.com/store/apps/details?id=edu.cornell.cs.multilist

A longer term goal is to make it use Fabric
(http://www.cs.cornell.edu/projects/Fabric) as the back end for persistent
storage. Using Fabric will allow checklists to be shared securely among
multiple users.

See the file LICENSE for information about using this code.

Andrew Myers, April 2017
