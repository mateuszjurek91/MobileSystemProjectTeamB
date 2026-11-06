# Map

The map starts over Poland and can be moved around the world. It uses bundled
Leaflet 1.9.4 and OpenStreetMap tiles. An internet connection is needed for tiles.
No location permission is requested: users choose a point by tapping the map.

The coloured polygons are fictional demo areas, not city boundaries or water
quality reports. The map and each area popup state this. Green means drinkable
and red means not drinkable within the demo. Outside these polygons the status
is unknown. These polygons must be replaced with attributed, dated information
before using the map for real water quality information.

Tap **Dodaj punkt**, select a location, enter a name and save it. Points are kept
on this device in SharedPreferences and remain after an app restart. They are
visible to local accounts on the same device, but are not uploaded to a server.
Tap a marker to see its name or give feedback. Owners can delete their points
with confirmation. User points and reports are unverified and do not change area colours.

Map tile requests send the displayed tile coordinates to OpenStreetMap.
Point names and saved point lists are not uploaded by the app.

Manual checks:

- Pan and zoom; check that attribution remains visible.
- Switch all three filters; only the matching demo polygons should remain.
- Add a point, switch tabs, restart the app and check its name and location.
- Cancel adding or deleting; existing points should remain unchanged.
- Delete a point, restart and check that it stays deleted.
- Disconnect and retry loading; check the error message and return to Tracker.

Leaflet licence: `project/app/src/main/assets/map/LEAFLET-LICENSE.txt`.
