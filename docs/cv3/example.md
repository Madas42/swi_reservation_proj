 Oblast                  | AS-IS                          | TO-BE                         | Akce   |
 |-------------------------|--------------------------------|-------------------------------|--------|
| změna Reservation.state | controller + service           | pouze owner lifecycle         | CHANGE |
| Notification API        | vendor SDK v application logic | izolováno v integration prvku | CHANGE |
| persistence dependency  | už odpovídá návrhu             | stejné                        | KEEP   |
| dependency rule         | neověřeno                      | pouze povolené směry          | VERIFY |