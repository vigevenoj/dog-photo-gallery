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
SELECT id, name, taken, metadata FROM photos
WHERE name = :name;

-- :name get-previous-years-photos :? :*
-- :doc retrieve dog photos from this day in previous years
SELECT id, name, taken, metadata from photos
WHERE extract(month from taken) = :month
AND extract(day from taken) = :day
ORDER BY taken desc;

-- :name get-recent-photos :? :*
-- :doc retrieve recently-taken dog photos
SELECT id, name, taken, metadata
FROM photos
ORDER BY taken DESC
LIMIT :limit