import BeautifulSoup, urllib2, json, re, os, sys, uuid, urlparse, pycountry, datetime, base64, urllib, StringIO
from ..common.OerWmFiles import *
from ..common.OerWmUrls import *


path = os.path.dirname(os.path.realpath(__file__)) + os.path.sep
uuid_file = path + "id_map.json"
uuids = {}
import_list = []


def read_until(buffer, delimiter_line):
    result = []
    line = buffer.readline()
    while line:
        if line.__eq__(delimiter_line):
            break
        else:
            result.append(line)
            line = buffer.readline()
    return result


def read_header(buffer):
    return read_until(buffer, "BEGIN:VEVENT\n")


def read_next_event(buffer):
    return read_until(buffer, "BEGIN:END\n")


def format_date(date_string):
    # TODO: For now, this function always returns a date time as UTC formatted. Implement time zone reference.
    if not date_string.endswith('Z'):
        date_string = date_string + 'Z'
    match = re.search(re.compile(r"([0-9]{4})([0-9]{2})([0-9]{2})T([0-9]{2})([0-9]{2})([0-9]{2})Z"), date_string)
    if match:
        return str(match.group(1) + '-' + match.group(2) + '-' + match.group(3) + 'T' + match.group(4) + ':' + match.group(5) + ':' + match.group(6) + 'Z')
    return date_string


def lines_to_resource(header, event, language):
    resource = {'@type': 'Event', '@context': 'https://oerworldmap.org/assets/json/context.json'}
    location = {}
    for line in event:
        if line.endswith('\n'):
            line = line[:-1]
        if line.startswith("SUMMARY:"):
            name = {'@value': line[8:], '@language': language}
            resource['name'] = [name]
        elif line.startswith("DTSTART:"):
            resource['startDate'] = format_date(line[8:])
        elif line.startswith("DTEND:"):
            resource['endDate'] = format_date(line[6:])
        elif line.startswith("GEO:"):
            coordinates = line[4:].split(";")
            geo = {'lat': coordinates[0], 'lon': coordinates[1]}
            location['geo'] = geo
        elif line.startswith("LOCATION:"):
            # TODO: split address into "addressCountry", "streetAddress", "postalCode" and "addressLocality"
            location['address'] = line[9:]
        elif line.startswith("ORGANIZER:"):
            name = {'@value': line[10:], '@language': language}
            organizer = {'name': [name]}
            # TODO: determine '@type' and '@id' of organizer
            resource['organizer'] = organizer
        elif line.startswith("UID:"):
            line = line[4:]
            if line.startswith("urn:uuid:"):
                resource['@id'] = line
            else:
                resource['@id'] = uuid.uuid4().urn
        elif line.startswith("URL:"):
            resource['url'] = line[4:]
        if location:
            resource['location'] = location
    return resource


def import_ical_from_string(page_content, language):
    imports = []
    buffer = StringIO.StringIO(page_content)
    header = read_header(buffer)
    event = read_next_event(buffer)
    while event:
        resource = lines_to_resource(header, event, language)
        imports.append(resource)
        line = buffer.readline()
        if line.__eq__("END:VCALENDAR"):
            break
        else:
            event = read_next_event(buffer)
    return imports


def import_ical_from_url(url, language):
    # this function expects a url purely providing a or several iCal event(s)
    if url.startswith('file://'):
        page_content = read_content_from_file(url, 'utf-8')
        # TODO: do we need file encoding as a parameter?
    else:
        page_content = read_content_from_url(url)
    if not language:
        language = 'en'
    imports = import_ical_from_string(page_content, language)
    return imports


def main():
    global path, uuids, import_list
    if len(sys.argv) != 5:
        print 'Usage: python -m import.iCal.import_ical <path>/<to>/import_ical.py <import_url> <language> <path>/<to>/<destination_file.json>'
        print 'Please provide the iCal event language as ISO 3166 ALPHA 2 country code.'
        return
    load_ids_from_file(path + "id_map.json", uuids)
    import_list = import_ical_from_url(sys.argv[2], sys.argv[3])
    save_ids_to_file(uuids, path + "id_map.json")
    return import_list


if __name__ == "__main__":
    import_list = main()
    # write_list_into_file(import_list, sys.argv[4])
    print "Events: " + `import_list`
