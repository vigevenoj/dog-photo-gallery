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
(name, taken, metadata, photo)
VALUES
(:name, :taken, :metadata, :photo)

-- :name get-dog-photo :? :1
-- :doc retrieves a dog photo given the id
SELECT * FROM photos
WHERE id = :id

-- :name delete-dog-photo! :! :n
-- :doc deletes a dog photo record given the id
DELETE FROM photos
WHERE id = :id