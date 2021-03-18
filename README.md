# doggallery

generated using Luminus version "3.91"

FIXME

## Prerequisites

You will need [Leiningen][1] 2.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein run 

## License

Copyright © 2021 FIXME


# TODO
* database schema
  * move photos to a separate table or external file/blob storage and map that from photo
* photo storage
  * store photos in object storage
  * stream photo through app to client
  * remove exif/xmp metadata from image sent to client
  * generate thumbnails
  * resize image to various sizes and provide a srcset for responsive design
* API
  * most recent photos (add pagination)