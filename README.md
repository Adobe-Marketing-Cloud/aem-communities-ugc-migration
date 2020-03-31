
# communities-template-migration

AEM Communities Upgrade Template Migration
Communities template miggratiom migrates custom templates (email, sites, groups, functions) from /etc/community/templates to /conf/global/settings/community/templates.
This migration is required for a customer upgrading to 6.4 or higher version from 6.3 or lower version. The templates are backward compatible and will work without migration as well but
the new templates will still be created under /etc only.

Steps to migrate :

1. build communities-template-migration bundle.
2. On each of the author and publish instances:
	- Stop the instance.
	- install the communities-template-migration bundle.
	- Make a HTTP POST call to http://<host>:<port>/libs/social/upgrade/templateMigrationServlet.json with ADMIN credentials.
	- Verify the logs if the migration completed successfully. 
	- Start the instance.
	- Validate from crx/de that /etc/community/templates is empty and existing templates are moved to /conf/global/settings/community/templates.


# communities-ugc-migration
AEM Communities UGC Migration Tool

2/22/2017
This product contains 4 distinct pieces:
- com.adobe.communities.ugc.migration.legacyExport: An exporter package for extracting user-generated content (UGC) from legacy versions of Adobe Experience Manager versions 5.6.1 and 6.0. Additionally, this package can export profile scores.
- com.adobe.communities.ugc.migration.legacyProfileExport: An exporter package specifically for exporting profile data (messages and social-graph) from AEM 6.0, where those features were introduced.
- package com.adobe.communities.ugc.migration: This provides both an exporter and an importer service for UGC and profile data into and out of AEM 6.1+.
- communities-ugc-migration-pkg: This package provides a graphical user interface for importing UGC into 6.1+. It must be installed in /crx/packMgr. The UI shows up in the admin section at "libs/social/console/content/importUpload.html".

# Supported types for UGC Migration
The following types of UGC can be migrated from AEM 5.6.1 or 6.0 into AEM 6.1:
1. Forums
2. Question and Answers
3. Comments
4. Journals
5. Calendars
6. Ratings
7. Scoring

Additionally, these additional types can be migrated based on version

a. From 6.0 to 6.1
1. Messages
2. Social Graphs (who follows who)

Since messages and social graphs can only be migrated from 6.0 (as they didn't exist in 5.6.1) there is a separate bundle that handles their export

b. From 6.4 and higher version, migration of below elements is also supported.
1. Activities
2. Notifications

# Exporting UGC using the legacy export servlet (works in both 5.6.1 and 6.0 installations)
1. Build the package "communities-ugc-migration-legacyExport" using maven.
2. Install the resulting .jar file in /system/console/bundles of the machine you want to export from.
3. Go to /crx/de and expand /content to find the root node for the component you wish to export (not within /content/usergenerated). Copy the path to the root of the components you wish to export.
4. In your browser, go to /services/social/ugc/export?path=[path you copied in crx/de]. This will trigger a file download of a zip archive containing all the UGC at or below the root node path you provided.
5. To export profile scores, you will need to look up the path to your system's profiles. By default, it is /home/users, but your value may be custom.
6. Go to /services/social/scores/export?path=/home/users (replace /home/users with your own value if it's different). Save the response as a json file.

# Exporting Messages and Social Graphs from a 6.0 system
1. Build the package "communities-ugc-migration-legacyProfileExport" using maven.
2. Install the resulting .jar file in /system/console/bundles of the machine you want to export from.
3. To export messages in a zip archive, navigate to /services/social/messages/export.
4. To export social graph, you will need to look up the path to your system's profiles. By default, it is /home/users, but your value may be custom.
6. Go to /services/social/graph/export?path=/home/users (replace /home/users with your own value if it's different). Save the response as a json file.

# Exporting UGC from AEM Communities 6.1+ (for backup or migration between instances)
1. Build the package "communities-ugc-migration" using maven.
2. Install the resulting .jar file in /system/console/bundles of the machine you want to export from.
3. Go to /crx/de and expand /content to find the root node for the component you wish to export (not within /content/usergenerated). Copy the path to the root of the components you wish to export.
4. In your browser, go to /services/social/ugc/export?path=[path you copied in crx/de]. This will trigger a file download of a zip archive containing all the UGC at or below the root node path you provided.

# Exporting Activity and Notification from AEM communitites 6.4+
1. Activity Export: http://hostname:port/services/social/activity/export - this will trigger a file download(activities.json) containing all the activities.
2. Notification Export: http://hostname:port/services/social/notification/export - this will trigger a file download(notifications.json) containing all the notifications

# Export Social-graphs
1. Export user-activity relation(User following another user activities)
   http://hostname:port/services/social/graph/content/export?path=/home/users&typeS=following&relType=USER&fileName=userActivities
   - As an output **userActivities.json** file will be downloaded.

2. Export user-notification relation(User following another user notification)
   http://hostname:port/services/social/graph/content/export?path=/home/users&typeS=notification&relType=USER&fileName=userNotification
   - As an output **userNotification.json** file will be downloaded.
   
3. Export relation between User and component (Forum, calendar, etc) resource Activities
   http://hostname:port/services/social/graph/content/export?path=/home/users&typeS=following&relType=RESOURCE&fileName=resourceActivities
   - As an output **resourceActivities.json** file will be downloaded.
   
4. Export relation between User and component (Forum, calendar etc.) resource notifications
   http://hostname:port/services/social/graph/content/export?path=/home/users&typeS=notification&relType=RESOURCE&fileName=resourceNotification
   - As an output **resourceNotification.json** file will be downloaded.

5. Export User following a component (Forum, calendar etc.) email subscription
   http://hostname:port/services/social/graph/content/export?path=/home/users&typeS=subscription&relType=RESOURCE&fileName=resourceSubscription
   - As an output **resourceSubscription.json** file will be downloaded.

Note: Everywehere replace ***/home/users*** with your own value if it's different)

# Importing UGC into 6.1+ using the import servlet with UI
1. Build the package "communities-ugc-migration" using maven.
2. Install the resulting .jar file in /system/console/bundles on a publish node of your AEM 6.1 instance where you wish to import.
3. Build the package "communities-ugc-migration-pkg" using maven.
4. Install the .zip package file in /crx/packMgr.
5. If you haven't already, you'll need to create the UGC component nodes for the content you want to migrate.
6. Go to http://hostname:port/system/console/configMgr, click on **AEM SocialEvent Handler** configuration and enable 'Set to disable Stream Providers'. Setting this configuration will prevent activity creation during UGC import.
7. In your browser, visit /libs/social/console/content/importUpload.html
8. Use the form on that page to upload the zip archive you downloaded during the export process.
9. The form on the page should expand to show a dropdown with a list of files extracted from the archive just uploaded, along with a text input field. The files from the archive are named according to their relative path below the root node you selected for export. Use that relative path to determine where in the new system you want that content to reside. For each file to be imported, select it from the dropdown, then put in the path to the UGC component node that matches the type of content you want to import and press the import button.

As the content is imported, the file containing that content will automatically be deleted from the system. If you want to delete, rather than import, any of the files extracted from the archive, just select them in the dropdown list and press the "delete" button.

# Importing UGC into 6.1+ using curl
1. Build the package "communities-ugc-migration" using maven.
2. Install the resulting .jar file in /system/console/bundles on a publish node of your AEM 6.1 instance where you wish to import.
3. If you haven't already, you'll need to create the UGC component nodes for the content you want to migrate.
4. Unzip the archive generated by the export function.
5. Send the individual json files to the import servlet by providing both the file and the path (as a URL parameter) to the UGC content node that should reference the imported content.
Example import command:
curl -i -u"admin:admin" -X POST -F"file=@/Users/sample/Downloads/export/en/community/hiking/calendar/jcr:content.json" http://localhost:4503/services/social/ugc/import?path=/content/geometrixx-outdoors/en/community/hiking/calendar/jcr:content/par/calendar

# Create Mapping file
1. Create **key.txt** file
   - key.txt file is required for importing Activities, Notifications and social graphs. It contains mapping of old vs new ids for 
     different resources.
2. After importing ugc, again export data using the [import servlet with UI](#Importing-UGC-into-6\.1\+-using-the-import-servlet-with-UI), this will download zip archive containing all the UGC at or below the root node path you provided along with **key.txt** file.
3. Each exported zips contains a key.txt files, Merge all these key.txt file into a single file.
4. In UNIX Merge all file using command, cat key1.txt key2.txt key3.txt >> key.txt

# Importing Activities and Notifications
### Pre-requisite
1. Supported in AEM version 6.4 and higher
2. Must have key.txt file [go to above section](#Create-Mapping-file)

### Activity and Notification Import Command
1. curl -i -u "admin:admin" -X POST "http://hostname:port/services/social/activity/import?offset=0" -F data=@/< absolute_path_to>/activities.json -F idMapping=@/<absolute_path_to>/key.txt
2. curl -i -u "admin:admin" -X POST "http://hostname:port/services/social/notification/import?offset=0" -F data=@/< absolute_path_to>/notifications.json -F idMapping=@/<absolute_path_to>/key.txt

#### Explanation
- offset - Initial index or row of “activities.json” or “notification.json” from where activity and notification import will start.
- Script maintains skipped activity and notification list (named as 'skippedActivities.txt' and 'skippedNotification.txt' respectively)   which will be created in publisher folder.
- This file contains all the skipped item indexes from the last script execution. In the next iteration, script will try to re-import     items present in the skipped file and new file will be created during each iteration.
- Example: 
  - If file contains [0 2 3] and offset value is 8 then while running script, import script will process only items present at index 0,      2, 3, 8 and after in `activities.json`. While executing script for given index if import process fails then that index will be          updated again in the file.
  - Every run requires updating of offset value. Otherwise duplicate activity and notification will get created.
  
Note: Once Activity and Notifications are imported, reset `set to disable Stream Providers' property under **AEM SocialEvent Handler** configuration.

# Import Social Graph using curl command
1. Import user-activity relation
   curl -i -u"admin:admin" -X POST 'http://hostname:port/services/social/graph/content/import?relType=USER&typeS= following' -F file=@/<    absolute_path_to>/userActivities.json -F idMapping=@/<absolute_path_to>/key.txt
2. Import user-notification relation
   curl -i -u"admin:admin" -X POST 'http://hostname:port/services/social/graph/content/import?relType=USER&typeS= notification' -F          file=@/< absolute_path_to> /userNotification.json -F idMapping=@/< absolute_path_to>/migration/export/key.txt
3. Import relation between User and component (Forum, calendar, etc) resource Activities
   curl -i -u"admin:admin" -X POST 'http://hostname:port/services/social/graph/content/import?relType=RESOURCE&t ypeS=following' -F        file=@/<absolute_path_to>/resourceActivities.json -F idMapping=@/<absolute_path_to>/export/key.txt
4. Import relation between User and component (Forum, calendar etc.) resource notification
   curl -i -u"admin:admin" -X POST 'http://hostname:port/services/social/graph/content/import?relType=RESOURCE&t ypeS=notification' -F      file=@/<absolute_path_to>/resourceNotification.json -F idMapping=@/<absolute_path_to>/key.txt
5. Export User following a component (Forum, calendar etc.) email subscription
   curl -i -u"admin:admin" -X POST 'http://hostname:port/services/social/graph/content/import?relType=RESOURCE&t ypeS=subscription' -F      file=@/<absolute_path_to>/resourceSubscription.json -F idMapping=@/<absolute_path_to>/key.txt

# Importing Profile Score data into 6.1+
1. Build the package "communities-ugc-migration" using maven.
2. Install the resulting .jar file in /system/console/bundles on a publish node of your AEM 6.1 instance where you wish to import.
3. Use curl to upload the scores file to the import servlet
4. Example import command:
curl -i -u"admin:admin" -X POST -F"file=@/Users/sample/Downloads/socialScores.json" http://localhost:4503/services/social/scores/import

# Importing Social Graph data into 6.1+
1. Build the package "communities-ugc-migration" using maven.
2. Install the resulting .jar file in /system/console/bundles on a publish node of your AEM 6.1 instance where you wish to import.
3. Use curl to upload the social graph file to the import servlet
Example import command:
curl -i -u"admin:admin" -X POST -F"file=@/Users/sample/Downloads/socialGraph.json" http://localhost:4503/services/social/graph/import

# Importing Messages into 6.1+
1. Build the package "communities-ugc-migration" using maven.
2. Install the resulting .jar file in /system/console/bundles on a publish node of your AEM 6.1 instance where you wish to import.
3. Use curl to upload the messages archive file to the import servlet
Example import command:
curl -i -u"admin:admin" -X POST -F"file=@/Users/sample/Downloads/export.zip" http://localhost:4503/services/social/messages/import

# communities-badges-migration
Communities Badges migration migrates badges paths from /etc/community/badging/images to new paths (/libs/community/badging/images, /content/community/badging/images).
This migration is required for a customer upgrading to 6.4 or higher version from 6.3 or lower version. If this migration is not done, User's old badges would continue
pointing to old paths(/etc).

Pre-requisite:
  - Make sure old badges images are present before running migration script.
  - Users should not be assigned any new badges prior migration as it might conflict with old badges during migration.

Steps to migrate :

1. Change the scoring path and badging paths in constant file (com.adobe.cq.social.badges.resource.migrator.internal.BadgingContants).
2. build cq-social-badges-migrator bundle.
3. On any of publisher instance:
	a. - Stop the instance.
	b. - install the cq-social-badges-migrator bundle.
	c. - Add a logger for package "com.adobe.cq.social.badges.resource"  from http://<host>:<port>/system/console/slinglog

4. Create New Badges

   - Make a HTTP POST call to http://<host>:<port>/libs/social/badges/badgeResourceCreateServlet with ADMIN credentials.
   - Check the logs for any error in log file created in 3c.

5. Validate Badges

   - Make a HTTP POST call to http://<host>:<port>/libs/social/badges/badgeResourceValidationServlet with ADMIN credentials.
   - Check the logs for any error in log file created in 3c.

6. Delete Old Badge - (If validation is passed for all Users in step 5)

   - Make a HTTP POST call to http://<host>:<port>/libs/social/badges/badgeResourceDeleteServlet with ADMIN credentials.
   - Check the logs for any error in log file created in 3c.

   Note-  This script currently handles single badgingrule and scoring rule configured on system. If string array of scoringrules and 
          badgingrules is configured on system, this might not work.

