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
  * photo to file mapping
  * photo date
  * photo metadata
* photo storage
  * store photos in object storage
  * stream photo through app to client
  * remove exif/xmp metadata from image sent to client
* API
  * most recent photos
  * photos x years ago (memories)
  * upload new photo (start with form method)