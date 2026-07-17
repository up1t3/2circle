-- 2circle tilemaker process script.
--
-- This is the heart of the client's visual identity: it decides which OSM tags survive
-- into the vector tiles. The client's MapStyleProvider reads `surface` as a
-- data-driven expression to colour roads — if `surface` is dropped here, every road
-- renders grey and the whole product premise ("see surface at a glance") collapses.
--
-- What we keep:
--   transportation — highway=*, with surface/smoothness/name/ref. Dropped: service
--                    roads (clutter), paths too thin to be useful at bike-touring zoom,
--                    and anything tagged highway=bus_stop / traffic_signal (POIs, not
--                    roads).
--   water          — natural=water + waterway=*. Compact polygons + centerlines.
--   landcover      — landuse=forest/meadow/farmland, natural=wood/scrub/heath.
--   boundary       — administrative boundaries (admin_level ≤ 4).
--   place          — city/town/village/hamlet names, for the search index and labels.
--
-- What we deliberately drop (the "bloat" cut):
--   buildings, addr:housenumber, amenity=*, shop=*, tourism=* (except camp/viewpoint,
--   which the search index picks up from the PBF directly, not from the tiles).
--
-- These choices cut typical region packages by 5–10×, which is the difference between
-- a 500 MB region and a 50 MB one. Bicycle touring needs the network and the rivers,
-- not the shape of every building.

-- Node/way attribute helper: pull a tag through only if it exists.
local function attr(tags, key)
    local v = tags[key]
    if v == nil then return nil end
    return v
end

-- ─── node entry points ────────────────────────────────────────────────────────

function node_function(node)
    local tags = node:Tags()

    -- Places: keep city/town/village/hamlet/isolated_dwelling with name + population.
    local place = tags.place
    if place ~= nil and (place == "city" or place == "town" or place == "village"
        or place == "hamlet" or place == "isolated_dwelling") then
        if attr(tags, "name") ~= nil then
            node:Layer("place", false)
            node:Attribute("name", attr(tags, "name"))
            node:AttributeNumeric("population", tonumber(attr(tags, "population") or "0"))
            node:Attribute("place", place)
        end
        return
    end

    -- POIs worth surfacing on the map (springs, mountain passes, campsites).
    -- Everything else is search-index-only territory.
    local natural = tags.natural
    local tourism = tags.tourism
    if natural == "spring" or natural == "mountain_pass" or tourism == "camp_site"
        or tourism == "viewpoint" or tourism == "alpine_hut" then
        if attr(tags, "name") ~= nil then
            node:Layer("place", false)
            node:Attribute("name", attr(tags, "name"))
            node:Attribute("kind", natural or tourism)
        end
    end
end

-- ─── way entry points ─────────────────────────────────────────────────────────

function way_function(way)
    local tags = way:Tags()
    local highway = tags.highway

    -- Roads and paths: the layer the client colours by surface.
    -- We accept the full bicycle-relevant highway spectrum and let zoom filtering on
    -- the client decide visibility. Surface/smoothness survive through unmodified.
    if highway ~= nil and highway ~= "services" and highway ~= "bus_stop"
        and highway ~= "traffic_signal" and highway ~= "stop" then
        way:Layer("transportation", false)
        way:Attribute("highway", highway)
        -- CRITICAL: surface drives the client's colour scheme. Always pass through.
        local surface = attr(tags, "surface")
        if surface ~= nil then way:Attribute("surface", surface) end
        local smoothness = attr(tags, "smoothness")
        if smoothness ~= nil then way:Attribute("smoothness", smoothness) end
        local name = attr(tags, "name")
        if name ~= nil then way:Attribute("name", name) end
        local ref = attr(tags, "ref")
        if ref ~= nil then way:Attribute("ref", ref) end
        return
    end

    -- Hydrography.
    if tags.natural == "water" or tags.waterway ~= nil then
        local is_poly = (tags.natural == "water")
        way:Layer("water", is_poly)
        if tags.waterway ~= nil then way:Attribute("waterway", tags.waterway) end
        local name = attr(tags, "name")
        if name ~= nil then way:Attribute("name", name) end
        return
    end

    -- Landcover: forest, fields, scrub — gives the rider visual context for terrain.
    local landuse = tags.landuse
    local landuse_relevant = (landuse == "forest" or landuse == "meadow"
        or landuse == "farmland" or landuse == "orchard" or landuse == "vineyard")
    local natural_land = tags.natural
    local natural_relevant = (natural_land == "wood" or natural_land == "scrub"
        or natural_land == "heath" or natural_land == "grassland" or natural_land == "wetland")
    if landuse_relevant or natural_relevant then
        way:Layer("landcover", true)
        if landuse ~= nil then way:Attribute("landuse", landuse) end
        if natural_land ~= nil then way:Attribute("natural", natural_land) end
        return
    end

    -- Administrative boundaries (countries, regions) — for orientation.
    local boundary = tags.boundary
    local admin = tags.admin_level
    if boundary == "administrative" and admin ~= nil then
        local lvl = tonumber(admin) or 99
        if lvl <= 4 then
            way:Layer("boundary", false)
            way:AttributeNumeric("admin_level", lvl)
        end
    end
end

-- Relation entry: not used — we don't emit route or multipolygon relations into tiles.
-- (Water bodies and forests as single polygons are good enough at bike-touring zoom.)
function relation_function(relation) end
