-- :name create-user! :! :n
-- :doc creates a new user record
INSERT INTO users
(id, first_name, last_name, email, pass)
VALUES (:id, :first_name, :last_name, :email, :pass)

-- :name update-user! :! :n
-- :doc updates an existing user record
UPDATE users
SET first_name = :first_name, last_name = :last_name, email = :email
WHERE id = :id

-- :name get-user :? :1
-- :doc retrieves a user record given the id
SELECT * FROM users
WHERE id = :id

-- :name delete-user! :! :n
-- :doc deletes a user record given the id
DELETE FROM users
WHERE id = :id


-- :name add-dog-photo! :! :n
-- :doc adds a new dog photo to the database
INSERT INTO photos
(name, userid, taken, metadata)
VALUES
(:name, :userid, :taken, :metadata)

-- :name update-photo-by-name! :! :n
-- :doc update a photo's database entry by name
UPDATE photos
SET taken = :taken, metadata = :metadata
WHERE name = :name

-- :name update-photo-by-id! :! :n
-- :doc update a photo's database entry by id
UPDATE photos
SET taken = :taken, metadata = :metadata
WHERE id = :id

-- :name get-dog-photo :? :1
-- :doc retrieves a dog photo given the id
SELECT id, name, taken, metadata FROM photos
WHERE id = :id

-- :name delete-dog-photo! :! :n
-- :doc deletes a dog photo record given the id
DELETE FROM photos
WHERE id = :id

-- :name get-dog-photo-by-uuid :? :1
-- :doc retrieve a dog photo given the uuid
SELECT p.id, p.name, p.taken, p.metadata,
(select name from photos where taken < p.taken order by taken desc limit 1) as older,
(select name from photos where taken > p.taken order by taken asc limit 1) as newer
FROM photos p
WHERE name = :name

-- :name get-previous-years-photos :? :*
-- :doc retrieve dog photos from this day in previous years
SELECT id, name, taken, metadata from photos
WHERE extract(month from taken) = :month::int
AND extract(day from taken) = :day::int
ORDER BY taken desc

-- :name get-recent-photos :? :*
-- :doc retrieve recently-taken dog photos
SELECT id, name, taken, metadata
FROM photos
WHERE taken is not null
ORDER BY taken DESC
LIMIT :limit

-- :name get-photos-by-date :? :*
-- :doc retrieve dog photos from a given date
SELECT id, name, taken, metadata
FROM photos
WHERE taken = :taken

-- :name get-photo-by-metadata :? :*
-- :doc retrieve photos with matching metadata
SELECT id, name, taken, metadata
FROM photos
WHERE metadata = :metadata

-- :name get-next-more-recent-by-timestamp :? :1
-- :doc retrieve the photo one newer than the given timestamp
SELECT id, name, taken, metadata
FROM photos
WHERE taken > :taken
ORDER BY taken asc
LIMIT 1

-- :name get-next-older-by-timestamp :? :1
-- :doc retrieve the photo one older than the given timestamp
SELECT id, name, taken, metadata
FROM photos
WHERE taken < :taken
ORDER BY taken desc
LIMIT 1

-- :name get-next-more-recent-photo :? :1
-- :doc get the photo one newer than the given uuid
SELECT id, name, taken, metadata
FROM photos
WHERE taken > (select taken from photos where name = :name)
ORDER BY taken asc
LIMIT 1

-- :name get-next-older-photo :? :1
-- :doc get the photo one older than the given uuid
SELECT id, name, taken, metadata
FROM photos
WHERE taken < (select taken from photos where name = :name)
ORDER BY taken desc
LIMIT 1

-- :name count-total-photos :? :1
-- :doc count the number of photos the system knows about
SELECT count(*)
FROM photos

-- :name count-processed-photos :? :1
-- :doc count the number of photos with metadata and taken-date available
SELECT count(*)
FROM photos
WHERE taken is not null

-- :name latest-photo-date :? :1
-- :doc get date of latest photo in database
SELECT max(taken) from photos;