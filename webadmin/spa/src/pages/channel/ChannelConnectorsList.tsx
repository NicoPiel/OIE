import React from 'react';
import type { components } from '../../../../oieapi-types/index.d.ts';

type Channel = components['schemas']['Channel'];

interface ChannelConnectorsListProps {
  channel: Channel;
}

const ChannelConnectorsList: React.FC<ChannelConnectorsListProps> = ({
  channel,
}) => {
  return (
    <div className='container mt-3'>
      <h3>Source Connector</h3>
      <div className='card mb-4'>
        <div className='card-body'>
          <h5 className='card-title'>{channel.sourceConnector.name}</h5>
          <p className='card-text'>
            Transport: {channel.sourceConnector.transportName}
          </p>
        </div>
      </div>

      <h3>Destination Connectors</h3>
      <table className='table table-striped'>
        <thead>
          <tr>
            <th>Name</th>
            <th>Transport</th>
          </tr>
        </thead>
        <tbody>
          {channel.destinationConnectors.map((connector, index) => (
            <tr key={index}>
              <td>{connector.name}</td>
              <td>{connector.transportName}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default ChannelConnectorsList;
